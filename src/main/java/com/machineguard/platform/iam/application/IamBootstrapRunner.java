package com.machineguard.platform.iam.application;

import com.machineguard.platform.iam.domain.events.IamEvents;
import com.machineguard.platform.iam.domain.model.OrganizationStatus;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.SubscriptionPlan;
import com.machineguard.platform.iam.domain.model.UserStatus;
import com.machineguard.platform.iam.infrastructure.persistence.BootstrapStateEntity;
import com.machineguard.platform.iam.infrastructure.persistence.OrganizationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.UserEntity;
import com.machineguard.platform.iam.infrastructure.persistence.repository.BootstrapStateJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.OrganizationJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "machineguard.iam.bootstrap", name = "enabled", havingValue = "true")
public class IamBootstrapRunner implements ApplicationRunner {
    private final BootstrapStateJpaRepository bootstrapState;
    private final OrganizationJpaRepository organizations;
    private final UserJpaRepository users;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String organizationName;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public IamBootstrapRunner(BootstrapStateJpaRepository bootstrapState, OrganizationJpaRepository organizations,
                              UserJpaRepository users, PasswordEncoder passwords, ApplicationEventPublisher events,
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
        if (bootstrapState.existsById("initial-admin")) return;
        if (organizations.count() > 0 || users.count() > 0) {
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
        var organization = organizations.save(new OrganizationEntity(UUID.randomUUID(), organizationName.trim(),
                SubscriptionPlan.FREE, OrganizationStatus.ACTIVE, null, now));
        var admin = users.save(new UserEntity(UUID.randomUUID(), organization.getId(), email,
                passwords.encode(adminPassword), adminName.trim(), Role.ADMIN, UserStatus.ACTIVE, now));
        bootstrapState.save(new BootstrapStateEntity("initial-admin", now));
        events.publishEvent(new IamEvents.OrganizationCreated(organization.getId(), organization.getName(), now));
        events.publishEvent(new IamEvents.UserRegistered(admin.getId(), organization.getId(), email, Role.ADMIN, now));
        events.publishEvent(new IamEvents.RoleAssigned(admin.getId(), organization.getId(), Role.ADMIN, now));
    }
}
