package com.machineguard.platform.iam.interfaces.rest;

import com.machineguard.platform.iam.application.IamService;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.machineguard.platform.iam.interfaces.rest.IamResources.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Users", description = "Authenticated user profile and organization user administration")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final IamService iam;

    public UserController(IamService iam) { this.iam = iam; }

    @GetMapping("/users/me")
    @Operation(summary = "Get the authenticated user's profile")
    public UserResource me(@AuthenticationPrincipal IamPrincipal principal) { return iam.profile(principal); }

    @PostMapping("/organizations/{organizationId}/users")
    @Operation(summary = "Register a user in the authenticated organization (ADMIN)")
    public ResponseEntity<UserResource> register(@AuthenticationPrincipal IamPrincipal principal,
                                                 @PathVariable UUID organizationId,
                                                 @Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(iam.registerUser(principal, organizationId,
                request.email(), request.password(), request.fullName(), request.role()));
    }

    @PatchMapping("/users/{userId}/role")
    @Operation(summary = "Assign an organization user role (ADMIN)")
    public UserResource assignRole(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID userId,
                                   @Valid @RequestBody AssignRoleRequest request) {
        return iam.assignRole(principal, userId, request.role());
    }

    @PatchMapping("/users/{userId}/status")
    @Operation(summary = "Activate or deactivate an organization user (ADMIN)")
    public UserResource changeStatus(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID userId,
                                     @Valid @RequestBody ChangeUserStatusRequest request) {
        return iam.changeUserStatus(principal, userId, request.status());
    }
}
