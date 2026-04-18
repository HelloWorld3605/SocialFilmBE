package com.filmbe.service;

import com.filmbe.dto.AdminDtos;
import com.filmbe.model.AuthPageImage;
import com.filmbe.repository.AuthPageImageRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthPageImageServiceTests {

    @Mock
    private AuthPageImageRepository authPageImageRepository;

    @InjectMocks
    private AuthPageImageService authPageImageService;

    @Test
    void listPublicReturnsNewestImagesFirst() {
        AuthPageImage first = image(4L, "https://cdn.example.com/new.webp", "New", "Newest", 62, 30, Instant.parse("2026-04-18T06:00:00Z"));
        AuthPageImage second = image(2L, "https://cdn.example.com/old.webp", "Old", "Older", 50, 50, Instant.parse("2026-04-12T06:00:00Z"));
        when(authPageImageRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(List.of(first, second));

        var response = authPageImageService.listPublic();

        assertEquals(2, response.items().size());
        assertEquals(first.getImageUrl(), response.items().get(0).imageUrl());
        assertEquals(62, response.items().get(0).focalPointX());
        assertEquals(30, response.items().get(0).focalPointY());
        assertEquals(second.getImageUrl(), response.items().get(1).imageUrl());
    }

    @Test
    void createTrimsOptionalCopyFields() {
        AuthPageImage saved = image(9L, "https://cdn.example.com/auth.webp", "Xu huong moi", null, 18, 74, Instant.parse("2026-04-18T07:00:00Z"));
        when(authPageImageRepository.save(org.mockito.ArgumentMatchers.any(AuthPageImage.class))).thenReturn(saved);

        authPageImageService.create(new AdminDtos.CreateAuthPageImageRequest(
                "https://cdn.example.com/auth.webp",
                "  Xu huong moi  ",
                "   ",
                18,
                74
        ));

        ArgumentCaptor<AuthPageImage> captor = ArgumentCaptor.forClass(AuthPageImage.class);
        verify(authPageImageRepository).save(captor.capture());
        assertEquals("https://cdn.example.com/auth.webp", captor.getValue().getImageUrl());
        assertEquals("Xu huong moi", captor.getValue().getTitle());
        assertNull(captor.getValue().getDescription());
        assertEquals(18, captor.getValue().getFocalPointX());
        assertEquals(74, captor.getValue().getFocalPointY());
    }

    @Test
    void createDefaultsAndClampsImagePosition() {
        AuthPageImage saved = image(11L, "https://cdn.example.com/auth-2.webp", null, null, 50, 100, Instant.parse("2026-04-18T08:00:00Z"));
        when(authPageImageRepository.save(org.mockito.ArgumentMatchers.any(AuthPageImage.class))).thenReturn(saved);

        authPageImageService.create(new AdminDtos.CreateAuthPageImageRequest(
                "https://cdn.example.com/auth-2.webp",
                null,
                null,
                null,
                999
        ));

        ArgumentCaptor<AuthPageImage> captor = ArgumentCaptor.forClass(AuthPageImage.class);
        verify(authPageImageRepository, org.mockito.Mockito.times(1)).save(captor.capture());
        assertEquals(50, captor.getValue().getFocalPointX());
        assertEquals(100, captor.getValue().getFocalPointY());
    }

    @Test
    void deleteRejectsMissingImage() {
        when(authPageImageRepository.findById(77L)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> authPageImageService.delete(77L)
        );

        assertEquals("Không tìm thấy ảnh auth page.", exception.getMessage());
    }

    private AuthPageImage image(
            Long id,
            String imageUrl,
            String title,
            String description,
            Integer focalPointX,
            Integer focalPointY,
            Instant createdAt
    ) {
        AuthPageImage image = new AuthPageImage();
        image.setId(id);
        image.setImageUrl(imageUrl);
        image.setTitle(title);
        image.setDescription(description);
        image.setFocalPointX(focalPointX);
        image.setFocalPointY(focalPointY);
        image.setCreatedAt(createdAt);
        return image;
    }
}
