package com.filmbe.controller;

import com.filmbe.dto.AuthDtos;
import com.filmbe.service.cloudinary.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuthDtos.UploadResponse upload(@RequestParam("file") MultipartFile file) {
        return new AuthDtos.UploadResponse(uploadService.upload(file));
    }
}
