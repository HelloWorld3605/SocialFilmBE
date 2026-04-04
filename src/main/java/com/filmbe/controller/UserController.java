package com.filmbe.controller;

import com.filmbe.dto.AuthDtos;
import com.filmbe.dto.UserDtos;
import com.filmbe.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/me")
    public AuthDtos.UserProfileResponse updateProfile(
            Authentication authentication,
            @Valid @RequestBody UserDtos.UpdateProfileRequest request
    ) {
        return userService.updateProfile(authentication.getName(), request);
    }
}

