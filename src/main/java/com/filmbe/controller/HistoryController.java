package com.filmbe.controller;

import com.filmbe.dto.LibraryDtos;
import com.filmbe.service.WatchHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final WatchHistoryService watchHistoryService;

    @GetMapping
    public List<LibraryDtos.WatchHistoryResponse> list(Authentication authentication) {
        return watchHistoryService.list(authentication.getName());
    }

    @PostMapping
    public LibraryDtos.WatchHistoryResponse save(
            Authentication authentication,
            @Valid @RequestBody LibraryDtos.SaveHistoryRequest request
    ) {
        return watchHistoryService.save(authentication.getName(), request);
    }
}

