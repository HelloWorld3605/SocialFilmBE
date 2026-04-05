package com.filmbe.service;

import com.filmbe.config.AppProperties;
import com.filmbe.dto.LibraryDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchProgressBufferService {

    private static final String DIRTY_SET_SUFFIX = "dirty";
    private static final String USER_INDEX_SUFFIX = "user-index";
    private static final String HISTORY_INDEX_SUFFIX = "history-index";

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    public Optional<BufferedWatchProgressState> find(Long userId, LibraryDtos.SaveHistoryRequest request) {
        try {
            return readState(progressKey(userId, request));
        } catch (RuntimeException exception) {
            log.warn("Failed to read buffered watch progress for user={} movie={}", userId, request.movieSlug(), exception);
            return Optional.empty();
        }
    }

    public Optional<BufferedWatchProgressState> save(
            Long userId,
            LibraryDtos.SaveHistoryRequest request,
            Long historyId,
            Instant updatedAt,
            boolean markFlushed
    ) {
        try {
            String key = progressKey(userId, request);
            BufferedWatchProgressState state = readState(key).orElseGet(BufferedWatchProgressState::new);

            Long previousHistoryId = state.getHistoryId();
            state.setHistoryId(historyId != null ? historyId : state.getHistoryId());
            state.setUserId(userId);
            state.setMovieSlug(request.movieSlug());
            state.setMovieName(request.movieName());
            state.setOriginName(request.originName());
            state.setPosterUrl(request.posterUrl());
            state.setThumbUrl(request.thumbUrl());
            state.setQuality(request.quality());
            state.setLang(request.lang());
            state.setYear(request.year());
            state.setLastEpisodeName(request.lastEpisodeName());
            state.setLastPositionSeconds(request.lastPositionSeconds());
            state.setLastServerIndex(request.lastServerIndex());
            state.setLastEpisodeIndex(request.lastEpisodeIndex());
            state.setDurationSeconds(request.durationSeconds());
            state.setUpdatedAt(updatedAt);

            if (markFlushed) {
                state.setLastFlushedAt(updatedAt);
                state.setLastFlushedPositionSeconds(request.lastPositionSeconds());
                state.setDirty(false);
            } else {
                state.setDirty(true);
            }

            writeState(key, state);

            if (previousHistoryId != null && !previousHistoryId.equals(state.getHistoryId())) {
                redisTemplate.delete(historyIndexKey(previousHistoryId));
            }

            return Optional.of(state);
        } catch (RuntimeException exception) {
            log.warn("Failed to buffer watch progress for user={} movie={}", userId, request.movieSlug(), exception);
            return Optional.empty();
        }
    }

    public List<BufferedWatchProgressState> listUserProgress(Long userId) {
        try {
            Set<String> keys = redisTemplate.opsForZSet().reverseRange(userIndexKey(userId), 0, -1);
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }

            List<BufferedWatchProgressState> items = new ArrayList<>();
            for (String key : keys) {
                readState(key).ifPresent(items::add);
            }

            items.sort(Comparator.comparing(BufferedWatchProgressState::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
            return items;
        } catch (RuntimeException exception) {
            log.warn("Failed to list buffered watch progress for user={}", userId, exception);
            return List.of();
        }
    }

    public List<BufferedWatchProgressState> listDirtyEntries(int limit) {
        try {
            Set<String> keys = redisTemplate.opsForSet().members(dirtySetKey());
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }

            List<BufferedWatchProgressState> items = new ArrayList<>();
            for (String key : keys.stream().limit(limit).toList()) {
                readState(key).ifPresent(items::add);
            }
            return items;
        } catch (RuntimeException exception) {
            log.warn("Failed to read dirty buffered watch progress entries", exception);
            return List.of();
        }
    }

    public void removeByHistoryId(Long userId, Long historyId) {
        try {
            String historyIndexKey = historyIndexKey(historyId);
            String progressKey = redisTemplate.opsForValue().get(historyIndexKey);
            if (progressKey == null || progressKey.isBlank()) {
                removeByScan(userId, historyId);
                return;
            }

            deleteProgressKey(userId, historyId, progressKey);
        } catch (RuntimeException exception) {
            log.warn("Failed to remove buffered watch progress for historyId={}", historyId, exception);
        }
    }

    private void removeByScan(Long userId, Long historyId) {
        for (BufferedWatchProgressState state : listUserProgress(userId)) {
            if (historyId.equals(state.getHistoryId())) {
                deleteProgressKey(userId, historyId, progressKey(userId, state.getMovieSlug(), state.getLastEpisodeIndex(), state.getLastEpisodeName()));
                return;
            }
        }
    }

    private void deleteProgressKey(Long userId, Long historyId, String progressKey) {
        redisTemplate.delete(progressKey);
        redisTemplate.delete(historyIndexKey(historyId));
        redisTemplate.opsForSet().remove(dirtySetKey(), progressKey);
        redisTemplate.opsForZSet().remove(userIndexKey(userId), progressKey);
    }

    private void writeState(String key, BufferedWatchProgressState state) {
        redisTemplate.delete(key);
        redisTemplate.opsForHash().putAll(key, toHash(state));
        redisTemplate.expire(key, appProperties.cache().watchProgress().redisTtl());

        redisTemplate.opsForZSet().add(
                userIndexKey(state.getUserId()),
                key,
                state.getUpdatedAt() == null ? Instant.now().toEpochMilli() : state.getUpdatedAt().toEpochMilli()
        );
        redisTemplate.expire(userIndexKey(state.getUserId()), appProperties.cache().watchProgress().redisTtl());

        if (state.getHistoryId() != null) {
            redisTemplate.opsForValue().set(
                    historyIndexKey(state.getHistoryId()),
                    key,
                    appProperties.cache().watchProgress().redisTtl()
            );
        }

        if (state.isDirty()) {
            redisTemplate.opsForSet().add(dirtySetKey(), key);
            redisTemplate.expire(dirtySetKey(), appProperties.cache().watchProgress().redisTtl());
        } else {
            redisTemplate.opsForSet().remove(dirtySetKey(), key);
        }
    }

    private Optional<BufferedWatchProgressState> readState(String key) {
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
        if (hash == null || hash.isEmpty()) {
            return Optional.empty();
        }

        BufferedWatchProgressState state = new BufferedWatchProgressState();
        state.setHistoryId(readLong(hash, "historyId"));
        state.setUserId(readLong(hash, "userId"));
        state.setMovieSlug(readText(hash, "movieSlug"));
        state.setMovieName(readText(hash, "movieName"));
        state.setOriginName(readText(hash, "originName"));
        state.setPosterUrl(readText(hash, "posterUrl"));
        state.setThumbUrl(readText(hash, "thumbUrl"));
        state.setQuality(readText(hash, "quality"));
        state.setLang(readText(hash, "lang"));
        state.setYear(readText(hash, "year"));
        state.setLastEpisodeName(readText(hash, "lastEpisodeName"));
        state.setLastPositionSeconds(readInteger(hash, "lastPositionSeconds"));
        state.setLastServerIndex(readInteger(hash, "lastServerIndex"));
        state.setLastEpisodeIndex(readInteger(hash, "lastEpisodeIndex"));
        state.setDurationSeconds(readInteger(hash, "durationSeconds"));
        state.setUpdatedAt(readInstant(hash, "updatedAt"));
        state.setLastFlushedAt(readInstant(hash, "lastFlushedAt"));
        state.setLastFlushedPositionSeconds(readInteger(hash, "lastFlushedPositionSeconds"));
        state.setDirty(Boolean.parseBoolean(readText(hash, "dirty")));
        return Optional.of(state);
    }

    private Map<String, String> toHash(BufferedWatchProgressState state) {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "historyId", state.getHistoryId());
        put(values, "userId", state.getUserId());
        put(values, "movieSlug", state.getMovieSlug());
        put(values, "movieName", state.getMovieName());
        put(values, "originName", state.getOriginName());
        put(values, "posterUrl", state.getPosterUrl());
        put(values, "thumbUrl", state.getThumbUrl());
        put(values, "quality", state.getQuality());
        put(values, "lang", state.getLang());
        put(values, "year", state.getYear());
        put(values, "lastEpisodeName", state.getLastEpisodeName());
        put(values, "lastPositionSeconds", state.getLastPositionSeconds());
        put(values, "lastServerIndex", state.getLastServerIndex());
        put(values, "lastEpisodeIndex", state.getLastEpisodeIndex());
        put(values, "durationSeconds", state.getDurationSeconds());
        put(values, "updatedAt", state.getUpdatedAt());
        put(values, "lastFlushedAt", state.getLastFlushedAt());
        put(values, "lastFlushedPositionSeconds", state.getLastFlushedPositionSeconds());
        values.put("dirty", Boolean.toString(state.isDirty()));
        return values;
    }

    private void put(Map<String, String> values, String key, Object value) {
        if (value == null) {
            return;
        }
        values.put(key, value.toString());
    }

    private String progressKey(Long userId, LibraryDtos.SaveHistoryRequest request) {
        return progressKey(userId, request.movieSlug(), request.lastEpisodeIndex(), request.lastEpisodeName());
    }

    private String progressKey(Long userId, String movieSlug, Integer lastEpisodeIndex, String lastEpisodeName) {
        return basePrefix() + "entry:" + encodeKeyPart(String.valueOf(userId)) + ":" + encodeKeyPart(movieSlug) + ":" + episodeIdentity(lastEpisodeIndex, lastEpisodeName);
    }

    private String userIndexKey(Long userId) {
        return basePrefix() + USER_INDEX_SUFFIX + ":" + encodeKeyPart(String.valueOf(userId));
    }

    private String historyIndexKey(Long historyId) {
        return basePrefix() + HISTORY_INDEX_SUFFIX + ":" + historyId;
    }

    private String dirtySetKey() {
        return basePrefix() + DIRTY_SET_SUFFIX;
    }

    private String episodeIdentity(Integer lastEpisodeIndex, String lastEpisodeName) {
        if (lastEpisodeIndex != null) {
            return "idx-" + lastEpisodeIndex;
        }
        if (lastEpisodeName != null && !lastEpisodeName.isBlank()) {
            return "name-" + encodeKeyPart(lastEpisodeName);
        }
        return "movie";
    }

    private String basePrefix() {
        return appProperties.cache().redis().keyPrefix() + "watch-progress:";
    }

    private String encodeKeyPart(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Long readLong(Map<Object, Object> hash, String key) {
        String value = readText(hash, key);
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private Integer readInteger(Map<Object, Object> hash, String key) {
        String value = readText(hash, key);
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private Instant readInstant(Map<Object, Object> hash, String key) {
        String value = readText(hash, key);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private String readText(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        return value == null ? null : value.toString();
    }
}
