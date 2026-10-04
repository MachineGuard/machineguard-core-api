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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.machineguard.platform.iam.interfaces.rest.IamResources.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "JWT access tokens and rotating refresh sessions")
public class AuthController {
    private final IamService iam;

    public AuthController(IamService iam) { this.iam = iam; }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with normalized email and password")
    public AuthenticationResource login(@Valid @RequestBody LoginRequest request) {
        var result = iam.login(request.email(), request.password());
        return new AuthenticationResource("Bearer", result.accessToken().value(), result.accessToken().expiresIn(),
                result.refreshToken(), result.user(), result.organization());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and issue a new access token")
    public AuthenticationResource refresh(@Valid @RequestBody RefreshRequest request) {
        var result = iam.refresh(request.refreshToken());
        return new AuthenticationResource("Bearer", result.accessToken().value(), result.accessToken().expiresIn(),
                result.refreshToken(), result.user(), result.organization());
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke the authenticated session's refresh token")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal IamPrincipal principal,
                                       @Valid @RequestBody LogoutRequest request) {
        iam.logout(principal, request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change the authenticated user's password and revoke all refresh sessions")
    public MessageResource changePassword(@AuthenticationPrincipal IamPrincipal principal,
                                          @Valid @RequestBody ChangePasswordRequest request) {
        iam.changePassword(principal, request.currentPassword(), request.newPassword());
        return new MessageResource("Password changed. Sign in again on your devices.");
    }

    @PostMapping("/validate")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Validate the current access token and return its tenant identity")
    public TokenValidationResource validate(@AuthenticationPrincipal IamPrincipal principal) {
        return iam.validateToken(principal);
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "Accept a one-time organization administrator invitation")
    public ResponseEntity<InvitationAcceptedResource> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(iam.acceptInvitation(request.token(), request.password()));
    }
}
