package com.filmbe.service;

import com.filmbe.dto.LibraryDtos;
import com.filmbe.model.User;
import com.filmbe.model.WatchHistory;
import com.filmbe.repository.UserRepository;
import com.filmbe.repository.WatchHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchHistoryService {

    private final UserRepository userRepository;
    private final WatchHistoryRepository watchHistoryRepository;

    public List<LibraryDtos.WatchHistoryResponse> list(String email) {
        User user = requireUser(email);
        return watchHistoryRepository.findTop20ByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LibraryDtos.WatchHistoryResponse save(String email, LibraryDtos.SaveHistoryRequest request) {
        User user = requireUser(email);
        WatchHistory history = watchHistoryRepository.findByUserIdAndMovieSlug(user.getId(), request.movieSlug())
                .orElseGet(WatchHistory::new);

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

        return toResponse(watchHistoryRepository.save(history));
    }

    private User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng."));
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
                history.getUpdatedAt()
        );
    }
}
