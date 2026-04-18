package com.filmbe.service;

import com.filmbe.dto.AdminDtos;
import com.filmbe.enums.Role;
import com.filmbe.model.User;
import com.filmbe.repository.FileResourceRepository;
import com.filmbe.repository.PendingRegistrationRepository;
import com.filmbe.repository.UserRepository;
import com.filmbe.repository.WatchHistoryRepository;
import com.filmbe.repository.WishlistItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceDeleteUserTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private FileResourceRepository fileResourceRepository;

    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void deleteUserRemovesDependentRowsBeforeDeletingUser() {
        User target = user(12L, "Nguyen Van A", "user@example.com", Role.USER);
        when(userRepository.findById(12L)).thenReturn(Optional.of(target));

        AdminDtos.ActionResponse response = adminService.deleteUser("admin@example.com", 12L);

        verify(wishlistItemRepository).deleteByUserId(12L);
        verify(watchHistoryRepository).deleteByUserId(12L);
        verify(userRepository).delete(target);
        assertEquals(
                "Đã xóa người dùng Nguyen Van A và toàn bộ dữ liệu gắn với tài khoản này.",
                response.message()
        );
    }

    @Test
    void deleteUserRejectsDeletingCurrentAdminAccount() {
        User target = user(3L, "Admin", "admin@example.com", Role.ADMIN);
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.deleteUser("admin@example.com", 3L)
        );

        assertEquals("Không thể tự xóa tài khoản admin đang đăng nhập.", exception.getMessage());
        verify(wishlistItemRepository, never()).deleteByUserId(3L);
        verify(watchHistoryRepository, never()).deleteByUserId(3L);
        verify(userRepository, never()).delete(target);
    }

    @Test
    void deleteUserRejectsDeletingLastAdmin() {
        User target = user(8L, "Last Admin", "other-admin@example.com", Role.ADMIN);
        when(userRepository.findById(8L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.deleteUser("admin@example.com", 8L)
        );

        assertEquals("Không thể xóa admin cuối cùng của hệ thống.", exception.getMessage());
        verify(wishlistItemRepository, never()).deleteByUserId(8L);
        verify(watchHistoryRepository, never()).deleteByUserId(8L);
        verify(userRepository, never()).delete(target);
    }

    private User user(Long id, String fullName, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
