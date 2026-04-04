package com.filmbe.service;

import com.filmbe.dto.LibraryDtos;
import com.filmbe.model.User;
import com.filmbe.model.WishlistItem;
import com.filmbe.repository.UserRepository;
import com.filmbe.repository.WishlistItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final UserRepository userRepository;
    private final WishlistItemRepository wishlistItemRepository;

    public List<LibraryDtos.LibraryMovieResponse> list(String email) {
        User user = requireUser(email);
        return wishlistItemRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public LibraryDtos.WishlistStateResponse state(String email, String movieSlug) {
        User user = requireUser(email);
        boolean wished = wishlistItemRepository.findByUserIdAndMovieSlug(user.getId(), movieSlug).isPresent();
        return new LibraryDtos.WishlistStateResponse(wished, list(email));
    }

    @Transactional
    public LibraryDtos.LibraryMovieResponse add(String email, LibraryDtos.SaveMovieRequest request) {
        User user = requireUser(email);
        WishlistItem item = wishlistItemRepository.findByUserIdAndMovieSlug(user.getId(), request.movieSlug())
                .orElseGet(WishlistItem::new);

        item.setUser(user);
        item.setMovieSlug(request.movieSlug());
        item.setMovieName(request.movieName());
        item.setOriginName(request.originName());
        item.setPosterUrl(request.posterUrl());
        item.setThumbUrl(request.thumbUrl());
        item.setQuality(request.quality());
        item.setLang(request.lang());
        item.setYear(request.year());

        return toResponse(wishlistItemRepository.save(item));
    }

    @Transactional
    public void remove(String email, String movieSlug) {
        User user = requireUser(email);
        wishlistItemRepository.deleteByUserIdAndMovieSlug(user.getId(), movieSlug);
    }

    private User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng."));
    }

    private LibraryDtos.LibraryMovieResponse toResponse(WishlistItem item) {
        return new LibraryDtos.LibraryMovieResponse(
                item.getId(),
                item.getMovieSlug(),
                item.getMovieName(),
                item.getOriginName(),
                item.getPosterUrl(),
                item.getThumbUrl(),
                item.getQuality(),
                item.getLang(),
                item.getYear(),
                item.getCreatedAt()
        );
    }
}

