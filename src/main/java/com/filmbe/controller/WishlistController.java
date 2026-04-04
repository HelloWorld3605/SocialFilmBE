package com.filmbe.controller;

import com.filmbe.dto.LibraryDtos;
import com.filmbe.service.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public List<LibraryDtos.LibraryMovieResponse> list(Authentication authentication) {
        return wishlistService.list(authentication.getName());
    }

    @GetMapping("/state")
    public LibraryDtos.WishlistStateResponse state(
            Authentication authentication,
            @RequestParam String movieSlug
    ) {
        return wishlistService.state(authentication.getName(), movieSlug);
    }

    @PostMapping
    public LibraryDtos.LibraryMovieResponse add(
            Authentication authentication,
            @Valid @RequestBody LibraryDtos.SaveMovieRequest request
    ) {
        return wishlistService.add(authentication.getName(), request);
    }

    @DeleteMapping("/{movieSlug}")
    public void remove(Authentication authentication, @PathVariable String movieSlug) {
        wishlistService.remove(authentication.getName(), movieSlug);
    }
}

