package com.filmbe.service;

import com.filmbe.config.AppProperties;
import com.filmbe.dto.LibraryDtos;
import com.filmbe.model.User;
import com.filmbe.model.WatchHistory;
import com.filmbe.repository.UserRepository;
import com.filmbe.repository.WatchHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchHistoryService {

    private static final String LEGACY_HISTORY_UNIQUE_INDEX = "uk_history_user_slug";
    private static final EnumSet<LibraryDtos.SaveHistoryReason> FORCE_FLUSH_REASONS = EnumSet.of(
            LibraryDtos.SaveHistoryReason.EMBED_OPEN,
            LibraryDtos.SaveHistoryReason.PAUSE,
            LibraryDtos.SaveHistoryReason.ENDED,
            LibraryDtos.SaveHistoryReason.BACKGROUND,
            LibraryDtos.SaveHistoryReason.EXIT
    );

    private final UserRepository userRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final JdbcTemplate jdbcTemplate;
    private final WatchProgressBufferService watchProgressBufferService;
    private final AppProperties appProperties;

    private volatile Boolean legacyMovieUniqueConstraint;

    public List<LibraryDtos.WatchHistoryResponse> list(String email) {
        User user = requireUser(email);
        List<LibraryDtos.WatchHistoryResponse> persisted = watchHistoryRepository.findAllByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();

        return mergeBufferedProgress(user.getId(), persisted);
    }

    @Transactional
    public LibraryDtos.WatchHistoryResponse save(String email, LibraryDtos.SaveHistoryRequest request) {
        User user = requireUser(email);
        Instant now = Instant.now();
        BufferedWatchProgressState buffered = watchProgressBufferService.find(user.getId(), request).orElse(null);

        if (shouldFlushToDatabase(request, buffered, now)) {
            WatchHistory persisted = persistHistory(user, request, buffered == null ? null : buffered.getHistoryId());
            watchProgressBufferService.save(
                    user.getId(),
                    request,
                    persisted.getId(),
                    persisted.getUpdatedAt() == null ? now : persisted.getUpdatedAt(),
                    true
            );
            return toResponse(persisted);
        }

        Optional<BufferedWatchProgressState> bufferedSave = watchProgressBufferService.save(
                user.getId(),
                request,
                buffered == null ? null : buffered.getHistoryId(),
                now,
                false
        );

        if (bufferedSave.isPresent() && bufferedSave.get().getHistoryId() != null) {
            return bufferedSave.get().toResponse();
        }

        WatchHistory persisted = persistHistory(user, request, buffered == null ? null : buffered.getHistoryId());
        watchProgressBufferService.save(
                user.getId(),
                request,
                persisted.getId(),
                persisted.getUpdatedAt() == null ? now : persisted.getUpdatedAt(),
                true
        );
        return toResponse(persisted);
    }

    @Transactional
    public void remove(String email, Long historyId) {
        User user = requireUser(email);
        long deleted = watchHistoryRepository.deleteByIdAndUserId(historyId, user.getId());
        if (deleted == 0) {
            throw new EntityNotFoundException("Không tìm thấy mục lịch sử xem.");
        }
        watchProgressBufferService.removeByHistoryId(user.getId(), historyId);
    }

    @Scheduled(fixedDelayString = "${app.cache.watch-progress.scheduler-delay:45s}")
    @Transactional
    public void flushBufferedProgress() {
        int batchSize = Math.max(1, appProperties.cache().watchProgress().schedulerBatchSize());
        for (BufferedWatchProgressState state : watchProgressBufferService.listDirtyEntries(batchSize)) {
            flushBufferedState(state);
        }
    }

    private void flushBufferedState(BufferedWatchProgressState state) {
        if (state.getUserId() == null || state.getHistoryId() == null) {
            return;
        }

        User user = userRepository.findById(state.getUserId()).orElse(null);
        if (user == null) {
            return;
        }

        LibraryDtos.SaveHistoryRequest request = new LibraryDtos.SaveHistoryRequest(
                state.getMovieSlug(),
                state.getMovieName(),
                state.getOriginName(),
                state.getPosterUrl(),
                state.getThumbUrl(),
                state.getQuality(),
                state.getLang(),
                state.getYear(),
                state.getLastEpisodeName(),
                state.getLastPositionSeconds(),
                state.getLastServerIndex(),
                state.getLastEpisodeIndex(),
                state.getDurationSeconds(),
                LibraryDtos.SaveHistoryReason.BACKGROUND
        );

        try {
            WatchHistory persisted = persistHistory(user, request, state.getHistoryId());
            watchProgressBufferService.save(
                    user.getId(),
                    request,
                    persisted.getId(),
                    persisted.getUpdatedAt() == null ? Instant.now() : persisted.getUpdatedAt(),
                    true
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to flush buffered watch progress for historyId={}", state.getHistoryId(), exception);
        }
    }

    private WatchHistory persistHistory(User user, LibraryDtos.SaveHistoryRequest request, Long knownHistoryId) {
        WatchHistory history = resolveHistory(user.getId(), knownHistoryId, request)
                .orElseGet(WatchHistory::new);

        applyRequest(history, user, request);
        return watchHistoryRepository.save(history);
    }

    private List<LibraryDtos.WatchHistoryResponse> mergeBufferedProgress(
            Long userId,
            List<LibraryDtos.WatchHistoryResponse> persisted
    ) {
        Map<String, LibraryDtos.WatchHistoryResponse> merged = new LinkedHashMap<>();
        for (LibraryDtos.WatchHistoryResponse item : persisted) {
            merged.put(historyKey(item.id(), item.movieSlug(), item.lastEpisodeIndex(), item.lastEpisodeName()), item);
        }

        for (BufferedWatchProgressState state : watchProgressBufferService.listUserProgress(userId)) {
            if (state.getHistoryId() == null || state.getUpdatedAt() == null) {
                continue;
            }

            LibraryDtos.WatchHistoryResponse candidate = state.toResponse();
            String key = historyKey(candidate.id(), candidate.movieSlug(), candidate.lastEpisodeIndex(), candidate.lastEpisodeName());
            LibraryDtos.WatchHistoryResponse current = merged.get(key);

            if (current == null || isAfter(candidate.updatedAt(), current.updatedAt())) {
                merged.put(key, candidate);
            }
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(LibraryDtos.WatchHistoryResponse::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    private String historyKey(Long id, String movieSlug, Integer lastEpisodeIndex, String lastEpisodeName) {
        if (id != null) {
            return "id:" + id;
        }
        if (lastEpisodeIndex != null) {
            return movieSlug + "::idx:" + lastEpisodeIndex;
        }
        if (lastEpisodeName != null && !lastEpisodeName.isBlank()) {
            return movieSlug + "::name:" + lastEpisodeName;
        }
        return movieSlug + "::movie";
    }

    private boolean isAfter(Instant candidate, Instant current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        return candidate.isAfter(current);
    }

    private boolean shouldFlushToDatabase(
            LibraryDtos.SaveHistoryRequest request,
            BufferedWatchProgressState buffered,
            Instant now
    ) {
        if (buffered == null || buffered.getHistoryId() == null) {
            return true;
        }

        if (request.saveReason() == null || FORCE_FLUSH_REASONS.contains(request.saveReason())) {
            return true;
        }

        if (buffered.getLastFlushedAt() == null) {
            return true;
        }

        Duration sinceLastFlush = Duration.between(buffered.getLastFlushedAt(), now);
        if (sinceLastFlush.compareTo(appProperties.cache().watchProgress().dbFlushInterval()) >= 0) {
            return true;
        }

        if (
                request.lastPositionSeconds() != null
                        && buffered.getLastFlushedPositionSeconds() != null
                        && Math.abs(request.lastPositionSeconds() - buffered.getLastFlushedPositionSeconds())
                        >= appProperties.cache().watchProgress().dbFlushPositionDeltaSeconds()
        ) {
            return true;
        }

        if (
                request.durationSeconds() != null
                        && request.lastPositionSeconds() != null
                        && request.durationSeconds() - request.lastPositionSeconds() <= 3
        ) {
            return true;
        }

        return false;
    }

    private Optional<WatchHistory> resolveHistory(
            Long userId,
            Long knownHistoryId,
            LibraryDtos.SaveHistoryRequest request
    ) {
        if (knownHistoryId != null) {
            Optional<WatchHistory> history = watchHistoryRepository.findByIdAndUserId(knownHistoryId, userId);
            if (history.isPresent()) {
                return history;
            }
        }

        if (request.lastEpisodeIndex() != null) {
            Optional<WatchHistory> history = watchHistoryRepository.findByUserIdAndMovieSlugAndLastEpisodeIndex(
                    userId,
                    request.movieSlug(),
                    request.lastEpisodeIndex()
            );
            if (history.isPresent()) {
                return history;
            }
        }

        if (request.lastEpisodeName() != null && !request.lastEpisodeName().isBlank()) {
            Optional<WatchHistory> history = watchHistoryRepository.findByUserIdAndMovieSlugAndLastEpisodeName(
                    userId,
                    request.movieSlug(),
                    request.lastEpisodeName()
            );
            if (history.isPresent()) {
                return history;
            }
        }

        if (usesLegacyMovieUniqueConstraint() || !hasEpisodeIdentity(request)) {
            return watchHistoryRepository.findTopByUserIdAndMovieSlugOrderByUpdatedAtDesc(
                    userId,
                    request.movieSlug()
            );
        }

        return Optional.empty();
    }

    private User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng."));
    }

    private void applyRequest(WatchHistory history, User user, LibraryDtos.SaveHistoryRequest request) {
        history.setUser(user);
        history.setMovieSlug(request.movieSlug());
        history.setMovieName(request.movieName());
        history.setOriginName(request.originName());
        history.setPosterUrl(request.posterUrl());
        history.setThumbUrl(request.thumbUrl());
        history.setQuality(request.quality());
        history.setLang(request.lang());
        history.setYear(request.year());
        history.setLastEpisodeName(request.lastEpisodeName());
        history.setLastPositionSeconds(request.lastPositionSeconds());
        history.setLastServerIndex(request.lastServerIndex());
        history.setLastEpisodeIndex(request.lastEpisodeIndex());
        history.setDurationSeconds(request.durationSeconds());
    }

    private boolean hasEpisodeIdentity(LibraryDtos.SaveHistoryRequest request) {
        return request.lastEpisodeIndex() != null
                || (request.lastEpisodeName() != null && !request.lastEpisodeName().isBlank());
    }

    private boolean usesLegacyMovieUniqueConstraint() {
        if (legacyMovieUniqueConstraint != null) {
            return legacyMovieUniqueConstraint;
        }

        synchronized (this) {
            if (legacyMovieUniqueConstraint != null) {
                return legacyMovieUniqueConstraint;
            }

            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'watch_history'
                      AND index_name = ?
                    """,
                    Integer.class,
                    LEGACY_HISTORY_UNIQUE_INDEX
            );
            legacyMovieUniqueConstraint = count != null && count > 0;
            return legacyMovieUniqueConstraint;
        }
    }

    private LibraryDtos.WatchHistoryResponse toResponse(WatchHistory history) {
        return new LibraryDtos.WatchHistoryResponse(
                history.getId(),
                history.getMovieSlug(),
                history.getMovieName(),
                history.getOriginName(),
                history.getPosterUrl(),
                history.getThumbUrl(),
                history.getQuality(),
                history.getLang(),
                history.getYear(),
                history.getLastEpisodeName(),
                history.getLastPositionSeconds(),
                history.getLastServerIndex(),
                history.getLastEpisodeIndex(),
                history.getDurationSeconds(),
                history.getUpdatedAt()
        );
    }
}
