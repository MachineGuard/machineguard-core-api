package com.machineguard.platform.iam.application;

import com.machineguard.platform.iam.domain.events.IamEvents;
import com.machineguard.platform.iam.domain.model.BootstrapState;
import com.machineguard.platform.iam.domain.model.Organization;
import com.machineguard.platform.iam.domain.model.OrganizationStatus;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.SubscriptionPlan;
import com.machineguard.platform.iam.domain.model.User;
import com.machineguard.platform.iam.domain.model.UserStatus;
import com.machineguard.platform.iam.domain.port.BootstrapStateRepository;
import com.machineguard.platform.iam.domain.port.OrganizationRepository;
import com.machineguard.platform.iam.domain.port.UserRepository;
import com.machineguard.platform.iam.application.port.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "machineguard.iam.bootstrap", name = "enabled", havingValue = "true")
public class IamBootstrapRunner implements ApplicationRunner {
    private final BootstrapStateRepository bootstrapState;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordHasher passwords;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String organizationName;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public IamBootstrapRunner(BootstrapStateRepository bootstrapState, OrganizationRepository organizations,
                              UserRepository users, PasswordHasher passwords, ApplicationEventPublisher events,
                              Clock clock, @Value("${machineguard.iam.bootstrap.organization-name:}") String organizationName,
                              @Value("${machineguard.iam.bootstrap.admin-name:}") String adminName,
                              @Value("${machineguard.iam.bootstrap.admin-email:}") String adminEmail,
                              @Value("${machineguard.iam.bootstrap.admin-password:}") String adminPassword) {
        this.bootstrapState = bootstrapState;
        this.organizations = organizations;
        this.users = users;
        this.passwords = passwords;
        this.events = events;
        this.clock = clock;
        this.organizationName = organizationName;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapState.findByKey("initial-admin").isPresent()) return;
        if (organizations.existsAny() || users.existsAny()) {
            throw new IllegalStateException("IAM bootstrap is only allowed before any organizations or users exist");
        }
        if (organizationName.isBlank() || adminName.isBlank() || adminEmail.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException("IAM bootstrap is enabled but the organization/admin environment settings are incomplete");
        }
        IamService.validatePassword(adminPassword);
        String email = IamService.normalizeEmail(adminEmail);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("IAM bootstrap email already belongs to an existing user");
        }
        var now = clock.instant();
        Organization organization = organizations.save(Organization.create(UUID.randomUUID(), organizationName.trim(),
                SubscriptionPlan.FREE, OrganizationStatus.ACTIVE, null, now));
        User admin = users.save(User.register(UUID.randomUUID(), organization.getId(), email,
                passwords.hash(adminPassword), adminName.trim(), Role.ADMIN, UserStatus.ACTIVE, now));
        bootstrapState.save(new BootstrapState("initial-admin", now));
        events.publishEvent(new IamEvents.OrganizationCreated(organization.getId(), organization.getName(), now));
        events.publishEvent(new IamEvents.UserRegistered(admin.getId(), organization.getId(), email, Role.ADMIN, now));
        events.publishEvent(new IamEvents.RoleAssigned(admin.getId(), organization.getId(), Role.ADMIN, now));
    }
}
