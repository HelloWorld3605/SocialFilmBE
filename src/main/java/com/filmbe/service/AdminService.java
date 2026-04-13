package com.filmbe.service;

import com.filmbe.dto.AdminDtos;
import com.filmbe.enums.Role;
import com.filmbe.model.PendingRegistration;
import com.filmbe.model.User;
import com.filmbe.model.WatchHistory;
import com.filmbe.model.WishlistItem;
import com.filmbe.repository.FileResourceRepository;
import com.filmbe.repository.PendingRegistrationRepository;
import com.filmbe.repository.UserRepository;
import com.filmbe.repository.WatchHistoryRepository;
import com.filmbe.repository.WishlistItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int RECENT_ACTIVITY_LIMIT = 10;
    private static final int LATEST_USERS_LIMIT = 6;
    private static final int TOP_MOVIES_LIMIT = 5;
    private static final int PENDING_REGISTRATION_LIMIT = 8;

    private final UserRepository userRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final FileResourceRepository fileResourceRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;

    @Transactional(readOnly = true)
    public AdminDtos.OverviewResponse overview() {
        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minus(Duration.ofDays(7));
        Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));

        List<User> latestUsers = userRepository.findAll(
                PageRequest.of(0, LATEST_USERS_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        AdminDtos.DashboardStats stats = new AdminDtos.DashboardStats(
                userRepository.count(),
                userRepository.countByRole(Role.ADMIN),
                userRepository.countByEmailVerifiedTrue(),
                userRepository.countByCreatedAtAfter(sevenDaysAgo),
                watchHistoryRepository.countDistinctActiveUsersByUpdatedAtAfter(sevenDaysAgo),
                pendingRegistrationRepository.countByExpiresAtAfter(now),
                watchHistoryRepository.count(),
                watchHistoryRepository.countByCreatedAtAfter(sevenDaysAgo),
                wishlistItemRepository.count(),
                wishlistItemRepository.countByCreatedAtAfter(sevenDaysAgo),
                fileResourceRepository.count(),
                fileResourceRepository.countByCreatedAtAfter(thirtyDaysAgo)
        );

        return new AdminDtos.OverviewResponse(
                stats,
                toUserSummaries(latestUsers),
                buildRecentActivity(),
                watchHistoryRepository.findTopMovieCounts(PageRequest.of(0, TOP_MOVIES_LIMIT))
                        .stream()
                        .map(item -> new AdminDtos.MovieMetric(
                                item.getMovieSlug(),
                                item.getMovieName(),
                                item.getThumbUrl(),
                                item.getTotal()
                        ))
                        .toList(),
                wishlistItemRepository.findTopMovieCounts(PageRequest.of(0, TOP_MOVIES_LIMIT))
                        .stream()
                        .map(item -> new AdminDtos.MovieMetric(
                                item.getMovieSlug(),
                                item.getMovieName(),
                                item.getThumbUrl(),
                                item.getTotal()
                        ))
                        .toList(),
                pendingRegistrationRepository.findAllByOrderByCreatedAtDesc(
                                PageRequest.of(0, PENDING_REGISTRATION_LIMIT)
                        )
                        .getContent()
                        .stream()
                        .map(this::toPendingRegistrationSummary)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public AdminDtos.UserListResponse listUsers(
            int page,
            int size,
            String query,
            Role role,
            Boolean verified
    ) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 50);

        Specification<User> specification = (root, cq, cb) -> cb.conjunction();
        if (query != null && !query.isBlank()) {
            String searchTerm = "%" + query.trim().toLowerCase() + "%";
            specification = specification.and((root, cq, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("fullName")), searchTerm),
                            cb.like(cb.lower(root.get("email")), searchTerm)
                    )
            );
        }
        if (role != null) {
            specification = specification.and((root, cq, cb) -> cb.equal(root.get("role"), role));
        }
        if (verified != null) {
            specification = specification.and((root, cq, cb) -> cb.equal(root.get("emailVerified"), verified));
        }

        var pageResult = userRepository.findAll(
                specification,
                PageRequest.of(
                        normalizedPage,
                        normalizedSize,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
                )
        );

        return new AdminDtos.UserListResponse(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements(),
                toUserSummaries(pageResult.getContent())
        );
    }

    @Transactional(readOnly = true)
    public AdminDtos.UserDetailResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng."));

        return new AdminDtos.UserDetailResponse(
                toUserSummary(user,
                        watchHistoryRepository.countByUserId(userId),
                        wishlistItemRepository.countByUserId(userId)),
                watchHistoryRepository.findTop5ByUserIdOrderByUpdatedAtDesc(userId)
                        .stream()
                        .map(this::toRecentWatchItem)
                        .toList(),
                wishlistItemRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId)
                        .stream()
                        .map(this::toRecentWishlistItem)
                        .toList()
        );
    }

    @Transactional
    public AdminDtos.UserDetailResponse updateUser(String actorEmail, Long userId, AdminDtos.UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng."));

        if (user.getRole() == Role.ADMIN
                && request.role() == Role.USER
                && userRepository.countByRole(Role.ADMIN) <= 1) {
            if (user.getEmail().equalsIgnoreCase(actorEmail)) {
                throw new IllegalArgumentException("Không thể tự hạ quyền admin cuối cùng của chính bạn.");
            }
            throw new IllegalArgumentException("Không thể hạ quyền admin cuối cùng của hệ thống.");
        }

        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setEmailVerified(request.emailVerified());
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        user.setBio(blankToNull(request.bio()));

        return getUser(user.getId());
    }

    @Transactional(readOnly = true)
    public AdminDtos.PendingRegistrationListResponse listPendingRegistrations(
            int page,
            int size,
            String query
    ) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 50);

        var pageRequest = PageRequest.of(
                normalizedPage,
                normalizedSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        var result = query == null || query.isBlank()
                ? pendingRegistrationRepository.findAllByOrderByCreatedAtDesc(pageRequest)
                : pendingRegistrationRepository.findAllByEmailContainingIgnoreCaseOrderByCreatedAtDesc(
                        query.trim(),
                        pageRequest
                );

        return new AdminDtos.PendingRegistrationListResponse(
                result.getNumber(),
                result.getSize(),
                result.getTotalPages(),
                result.getTotalElements(),
                result.getContent().stream()
                        .map(this::toPendingRegistrationSummary)
                        .toList()
        );
    }

    @Transactional
    public AdminDtos.ActionResponse resetPendingRegistration(Long pendingRegistrationId) {
        PendingRegistration pendingRegistration = pendingRegistrationRepository.findById(pendingRegistrationId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy yêu cầu đăng ký chờ xử lý."));

        String email = pendingRegistration.getEmail();
        pendingRegistrationRepository.delete(pendingRegistration);

        return new AdminDtos.ActionResponse(
                "Đã reset trạng thái chờ đăng ký cho " + email + ". Email này có thể đăng ký lại ngay."
        );
    }

    @Transactional
    public AdminDtos.ActionResponse resetPendingRegistrationByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        PendingRegistration pendingRegistration = pendingRegistrationRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Email này hiện không có trạng thái chờ đăng ký."));

        pendingRegistrationRepository.delete(pendingRegistration);

        return new AdminDtos.ActionResponse(
                "Đã reset trạng thái chờ đăng ký cho " + normalizedEmail + ". Email này có thể đăng ký lại ngay."
        );
    }

    private List<AdminDtos.UserSummary> toUserSummaries(List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = users.stream().map(User::getId).toList();
        Map<Long, Long> watchCounts = new LinkedHashMap<>();
        Map<Long, Long> wishlistCounts = new LinkedHashMap<>();

        watchHistoryRepository.countGroupedByUserIds(userIds)
                .forEach(item -> watchCounts.put(item.getUserId(), item.getTotal()));
        wishlistItemRepository.countGroupedByUserIds(userIds)
                .forEach(item -> wishlistCounts.put(item.getUserId(), item.getTotal()));

        return users.stream()
                .map(user -> toUserSummary(
                        user,
                        watchCounts.getOrDefault(user.getId(), 0L),
                        wishlistCounts.getOrDefault(user.getId(), 0L)
                ))
                .toList();
    }

    private AdminDtos.UserSummary toUserSummary(User user, long watchHistoryCount, long wishlistCount) {
        return new AdminDtos.UserSummary(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getAvatarUrl(),
                user.getBio(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                watchHistoryCount,
                wishlistCount
        );
    }

    private List<AdminDtos.ActivityItem> buildRecentActivity() {
        List<AdminDtos.ActivityItem> items = new ArrayList<>();

        watchHistoryRepository.findTop8ByOrderByUpdatedAtDesc()
                .forEach(history -> items.add(new AdminDtos.ActivityItem(
                        "WATCH",
                        history.getUser().getId(),
                        history.getUser().getFullName(),
                        history.getUser().getEmail(),
                        history.getMovieSlug(),
                        history.getMovieName(),
                        history.getLastEpisodeName() == null || history.getLastEpisodeName().isBlank()
                                ? "Tiếp tục xem phim"
                                : "Tua/xem tới " + history.getLastEpisodeName(),
                        history.getUpdatedAt()
                )));

        wishlistItemRepository.findTop8ByOrderByCreatedAtDesc()
                .forEach(item -> items.add(new AdminDtos.ActivityItem(
                        "WISHLIST",
                        item.getUser().getId(),
                        item.getUser().getFullName(),
                        item.getUser().getEmail(),
                        item.getMovieSlug(),
                        item.getMovieName(),
                        "Thêm phim vào danh sách xem sau",
                        item.getCreatedAt()
                )));

        return items.stream()
                .sorted(Comparator.comparing(AdminDtos.ActivityItem::occurredAt).reversed())
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();
    }

    private AdminDtos.RecentWatchItem toRecentWatchItem(WatchHistory item) {
        return new AdminDtos.RecentWatchItem(
                item.getId(),
                item.getMovieSlug(),
                item.getMovieName(),
                item.getOriginName(),
                item.getThumbUrl(),
                item.getLastEpisodeName(),
                item.getLastPositionSeconds(),
                item.getDurationSeconds(),
                item.getUpdatedAt()
        );
    }

    private AdminDtos.RecentWishlistItem toRecentWishlistItem(WishlistItem item) {
        return new AdminDtos.RecentWishlistItem(
                item.getId(),
                item.getMovieSlug(),
                item.getMovieName(),
                item.getOriginName(),
                item.getThumbUrl(),
                item.getCreatedAt()
        );
    }

    private AdminDtos.PendingRegistrationSummary toPendingRegistrationSummary(PendingRegistration item) {
        Instant now = Instant.now();
        return new AdminDtos.PendingRegistrationSummary(
                item.getId(),
                item.getEmail(),
                item.getCreatedAt(),
                item.getExpiresAt(),
                item.getExpiresAt() != null && item.getExpiresAt().isBefore(now)
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
