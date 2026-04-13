package com.filmbe.controller;

import com.filmbe.dto.AdminDtos;
import com.filmbe.enums.Role;
import com.filmbe.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/overview")
    public AdminDtos.OverviewResponse overview() {
        return adminService.overview();
    }

    @GetMapping("/users")
    public AdminDtos.UserListResponse users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean verified
    ) {
        return adminService.listUsers(page, size, query, role, verified);
    }

    @GetMapping("/users/{userId}")
    public AdminDtos.UserDetailResponse user(@PathVariable Long userId) {
        return adminService.getUser(userId);
    }

    @GetMapping("/pending-registrations")
    public AdminDtos.PendingRegistrationListResponse pendingRegistrations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String query
    ) {
        return adminService.listPendingRegistrations(page, size, query);
    }

    @PutMapping("/users/{userId}")
    public AdminDtos.UserDetailResponse updateUser(
            Authentication authentication,
            @PathVariable Long userId,
            @Valid @RequestBody AdminDtos.UpdateUserRequest request
    ) {
        return adminService.updateUser(authentication.getName(), userId, request);
    }

    @DeleteMapping("/pending-registrations/{pendingRegistrationId}")
    public AdminDtos.ActionResponse resetPendingRegistration(@PathVariable Long pendingRegistrationId) {
        return adminService.resetPendingRegistration(pendingRegistrationId);
    }

    @DeleteMapping("/pending-registrations")
    public AdminDtos.ActionResponse resetPendingRegistrationByEmail(
            @Valid @RequestBody AdminDtos.ResetPendingRegistrationRequest request
    ) {
        return adminService.resetPendingRegistrationByEmail(request.email());
    }
}
