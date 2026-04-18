package com.filmbe.dto;

import com.filmbe.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record DashboardStats(
            long totalUsers,
            long totalAdmins,
            long verifiedUsers,
            long newUsersLast7Days,
            long activeUsersLast7Days,
            long pendingRegistrations,
            long totalWatchHistoryEntries,
            long watchHistoryLast7Days,
            long totalWishlistItems,
            long wishlistItemsLast7Days,
            long totalUploads,
            long uploadsLast30Days
    ) {
    }

    public record UserSummary(
            Long id,
            String fullName,
            String email,
            String role,
            String avatarUrl,
            String bio,
            boolean emailVerified,
            Instant createdAt,
            Instant updatedAt,
            long watchHistoryCount,
            long wishlistCount
    ) {
    }

    public record MovieMetric(
            String movieSlug,
            String movieName,
            String thumbUrl,
            long total
    ) {
    }

    public record ActivityItem(
            String type,
            Long userId,
            String userName,
            String userEmail,
            String movieSlug,
            String movieName,
            String detail,
            Instant occurredAt
    ) {
    }

    public record RecentWatchItem(
            Long id,
            String movieSlug,
            String movieName,
            String originName,
            String thumbUrl,
            String lastEpisodeName,
            Integer lastPositionSeconds,
            Integer durationSeconds,
            Instant updatedAt
    ) {
    }

    public record RecentWishlistItem(
            Long id,
            String movieSlug,
            String movieName,
            String originName,
            String thumbUrl,
            Instant createdAt
    ) {
    }

    public record PendingRegistrationSummary(
            Long id,
            String email,
            Instant createdAt,
            Instant expiresAt,
            boolean expired
    ) {
    }

    public record OverviewResponse(
            DashboardStats stats,
            List<UserSummary> latestUsers,
            List<ActivityItem> recentActivity,
            List<MovieMetric> topWatchedMovies,
            List<MovieMetric> topWishlistedMovies,
            List<PendingRegistrationSummary> pendingRegistrations
    ) {
    }

    public record PendingRegistrationListResponse(
            int page,
            int size,
            int totalPages,
            long totalItems,
            List<PendingRegistrationSummary> items
    ) {
    }

    public record ActionResponse(String message) {
    }

    public record AuthPageImageItem(
            Long id,
            String imageUrl,
            String title,
            String description,
            Integer focalPointX,
            Integer focalPointY,
            Instant createdAt
    ) {
    }

    public record AuthPageImageListResponse(
            List<AuthPageImageItem> items
    ) {
    }

    public record CreateAuthPageImageRequest(
            @NotBlank @Size(max = 1000) String imageUrl,
            @Size(max = 120) String title,
            @Size(max = 320) String description,
            @Min(0) @Max(100) Integer focalPointX,
            @Min(0) @Max(100) Integer focalPointY
    ) {
    }

    public record UpdateAuthPageImageRequest(
            @NotBlank @Size(max = 1000) String imageUrl,
            @Size(max = 120) String title,
            @Size(max = 320) String description,
            @Min(0) @Max(100) Integer focalPointX,
            @Min(0) @Max(100) Integer focalPointY
    ) {
    }

    public record ResetPendingRegistrationRequest(
            @NotBlank @Email @Size(max = 160) String email
    ) {
    }

    public record UserListResponse(
            int page,
            int size,
            int totalPages,
            long totalItems,
            List<UserSummary> items
    ) {
    }

    public record UserDetailResponse(
            UserSummary user,
            List<RecentWatchItem> recentWatchHistory,
            List<RecentWishlistItem> recentWishlist
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotNull Role role,
            boolean emailVerified,
            @Size(max = 255) String avatarUrl,
            @Size(max = 500) String bio
    ) {
    }
}
