package com.urlshortener.controller;

import com.urlshortener.dto.BlockDomainRequest;
import com.urlshortener.model.BlockedDomain;
import com.urlshortener.model.User;
import com.urlshortener.repository.UserRepository;
import com.urlshortener.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only moderation endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;

    @PostMapping("/block-domain")
    @Operation(summary = "Block a domain from being used as a redirect target")
    public ResponseEntity<BlockedDomain> blockDomain(
            @Valid @RequestBody BlockDomainRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long adminId = resolveAdminId(userDetails);
        return ResponseEntity.ok(adminService.blockDomain(request, adminId));
    }

    @DeleteMapping("/block-domain/{id}")
    @Operation(summary = "Unblock a previously blocked domain")
    public ResponseEntity<Void> unblockDomain(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        adminService.unblockDomain(id, resolveAdminId(userDetails));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/block-domain")
    @Operation(summary = "List all active blocked domains")
    public ResponseEntity<List<BlockedDomain>> listBlocked() {
        return ResponseEntity.ok(adminService.listBlockedDomains());
    }

    @PutMapping("/urls/{id}/deactivate")
    @Operation(summary = "Deactivate a suspicious URL globally")
    public ResponseEntity<Void> deactivateUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        adminService.deactivateUrl(id, resolveAdminId(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/ban")
    @Operation(summary = "Ban a user account")
    public ResponseEntity<Void> banUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        adminService.banUser(id, resolveAdminId(userDetails));
        return ResponseEntity.noContent().build();
    }

    private Long resolveAdminId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(User::getId)
                .orElse(null);
    }
}
