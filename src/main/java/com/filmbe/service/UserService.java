package com.filmbe.service;

import com.filmbe.dto.AuthDtos;
import com.filmbe.dto.UserDtos;
import com.filmbe.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthService authService;

    @Transactional
    public AuthDtos.UserProfileResponse updateProfile(String email, UserDtos.UpdateProfileRequest request) {
        var user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng."));

        user.setFullName(request.fullName().trim());
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        user.setBio(blankToNull(request.bio()));

        return authService.toProfile(user);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

