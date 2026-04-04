package com.filmbe.service;

import com.filmbe.dto.LibraryDtos;
import com.filmbe.model.User;
import com.filmbe.model.WatchHistory;
import com.filmbe.repository.UserRepository;
import com.filmbe.repository.WatchHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WatchHistoryService {

    private static final String LEGACY_HISTORY_UNIQUE_INDEX = "uk_history_user_slug";

    private final UserRepository userRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final JdbcTemplate jdbcTemplate;

    public List<LibraryDtos.WatchHistoryResponse> list(String email) {
        User user = requireUser(email);
        return watchHistoryRepository.findAllByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LibraryDtos.WatchHistoryResponse save(String email, LibraryDtos.SaveHistoryRequest request) {
        User user = requireUser(email);
        WatchHistory history = resolveHistory(user.getId(), request)
                .orElseGet(WatchHistory::new);

        applyRequest(history, user, request);

        return toResponse(watchHistoryRepository.save(history));
    }

    private Optional<WatchHistory> resolveHistory(Long userId, LibraryDtos.SaveHistoryRequest request) {
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
        return count != null && count > 0;
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
