package com.filmbe.dto;

import com.filmbe.enums.Role;
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

    public record OverviewResponse(
            DashboardStats stats,
            List<UserSummary> latestUsers,
            List<ActivityItem> recentActivity,
            List<MovieMetric> topWatchedMovies,
            List<MovieMetric> topWishlistedMovies
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
