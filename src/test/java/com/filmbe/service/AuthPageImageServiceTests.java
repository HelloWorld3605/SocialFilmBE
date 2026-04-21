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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthPageImageServiceTests {

    @Mock
    private AuthPageImageRepository authPageImageRepository;

    @InjectMocks
    private AuthPageImageService authPageImageService;

    @Test
    void listPublicReturnsImagesByDisplayOrder() {
        AuthPageImage first = image(4L, "https://cdn.example.com/hero.webp", "Hero", "First", 1, 62, 30, Instant.parse("2026-04-18T06:00:00Z"));
        AuthPageImage second = image(2L, "https://cdn.example.com/promo.webp", "Promo", "Second", 3, 50, 50, Instant.parse("2026-04-12T06:00:00Z"));
        when(authPageImageRepository.findAllByOrderByDisplayOrderAscCreatedAtDescIdDesc()).thenReturn(List.of(first, second));

        var response = authPageImageService.listPublic();

        assertEquals(2, response.items().size());
        assertEquals(first.getImageUrl(), response.items().get(0).imageUrl());
        assertEquals(1, response.items().get(0).displayOrder());
        assertEquals(62, response.items().get(0).focalPointX());
        assertEquals(30, response.items().get(0).focalPointY());
        assertEquals(second.getImageUrl(), response.items().get(1).imageUrl());
    }

    @Test
    void createTrimsOptionalCopyFields() {
        AuthPageImage saved = image(9L, "https://cdn.example.com/auth.webp", "Xu huong moi", null, 5, 18, 74, Instant.parse("2026-04-18T07:00:00Z"));
        when(authPageImageRepository.save(org.mockito.ArgumentMatchers.any(AuthPageImage.class))).thenReturn(saved);

        authPageImageService.create(new AdminDtos.CreateAuthPageImageRequest(
                "https://cdn.example.com/auth.webp",
                "  Xu huong moi  ",
                "   ",
                5,
                18,
                74
        ));

        ArgumentCaptor<AuthPageImage> captor = ArgumentCaptor.forClass(AuthPageImage.class);
        verify(authPageImageRepository).save(captor.capture());
        assertEquals("https://cdn.example.com/auth.webp", captor.getValue().getImageUrl());
        assertEquals("Xu huong moi", captor.getValue().getTitle());
        assertNull(captor.getValue().getDescription());
        assertEquals(5, captor.getValue().getDisplayOrder());
        assertEquals(18, captor.getValue().getFocalPointX());
        assertEquals(74, captor.getValue().getFocalPointY());
    }

    @Test
    void createDefaultsDisplayOrderAndClampsImagePosition() {
        AuthPageImage latest = image(7L, "https://cdn.example.com/hero.webp", "Hero", "Lead", 4, 50, 50, Instant.parse("2026-04-18T06:00:00Z"));
        AuthPageImage saved = image(11L, "https://cdn.example.com/auth-2.webp", null, null, 5, 50, 100, Instant.parse("2026-04-18T08:00:00Z"));
        when(authPageImageRepository.findFirstByOrderByDisplayOrderDescIdDesc()).thenReturn(Optional.of(latest));
        when(authPageImageRepository.save(org.mockito.ArgumentMatchers.any(AuthPageImage.class))).thenReturn(saved);

        authPageImageService.create(new AdminDtos.CreateAuthPageImageRequest(
                "https://cdn.example.com/auth-2.webp",
                null,
                null,
                null,
                null,
                999
        ));

        ArgumentCaptor<AuthPageImage> captor = ArgumentCaptor.forClass(AuthPageImage.class);
        verify(authPageImageRepository, org.mockito.Mockito.times(1)).save(captor.capture());
        assertEquals(5, captor.getValue().getDisplayOrder());
        assertEquals(50, captor.getValue().getFocalPointX());
        assertEquals(100, captor.getValue().getFocalPointY());
    }

    @Test
    void updateKeepsDisplayOrderWhenClientDoesNotSendIt() {
        AuthPageImage existing = image(5L, "https://cdn.example.com/current.webp", "Current", "Current", 7, 50, 50, Instant.parse("2026-04-18T08:00:00Z"));
        when(authPageImageRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(authPageImageRepository.save(existing)).thenReturn(existing);

        authPageImageService.update(5L, new AdminDtos.UpdateAuthPageImageRequest(
                "https://cdn.example.com/current.webp",
                " Current ",
                " Updated ",
                null,
                24,
                88
        ));

        assertEquals(7, existing.getDisplayOrder());
        assertEquals("Current", existing.getTitle());
        assertEquals("Updated", existing.getDescription());
        assertEquals(24, existing.getFocalPointX());
        assertEquals(88, existing.getFocalPointY());
    }

    @Test
    void createRejectsDuplicateDisplayOrder() {
        when(authPageImageRepository.existsByDisplayOrder(5)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authPageImageService.create(new AdminDtos.CreateAuthPageImageRequest(
                        "https://cdn.example.com/auth.webp",
                        "Hero",
                        null,
                        5,
                        50,
                        50
                ))
        );

        assertEquals(
                "Số thứ tự này đã được dùng cho ảnh khác. Hãy dùng popup sắp xếp để đổi vị trí.",
                exception.getMessage()
        );
    }

    @Test
    void updateRejectsDuplicateDisplayOrder() {
        AuthPageImage existing = image(5L, "https://cdn.example.com/current.webp", "Current", "Current", 7, 50, 50, Instant.parse("2026-04-18T08:00:00Z"));
        when(authPageImageRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(authPageImageRepository.existsByDisplayOrderAndIdNot(3, 5L)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authPageImageService.update(5L, new AdminDtos.UpdateAuthPageImageRequest(
                        "https://cdn.example.com/current.webp",
                        "Current",
                        null,
                        3,
                        50,
                        50
                ))
        );

        assertEquals(
                "Số thứ tự này đã được dùng cho ảnh khác. Hãy dùng popup sắp xếp để đổi vị trí.",
                exception.getMessage()
        );
    }

    @Test
    void reorderRenumbersImagesSequentially() {
        AuthPageImage first = image(4L, "https://cdn.example.com/hero.webp", "Hero", "First", 1, 62, 30, Instant.parse("2026-04-18T06:00:00Z"));
        AuthPageImage second = image(7L, "https://cdn.example.com/promo.webp", "Promo", "Second", 2, 50, 50, Instant.parse("2026-04-12T06:00:00Z"));
        AuthPageImage third = image(8L, "https://cdn.example.com/spotlight.webp", "Spotlight", "Third", 3, 44, 51, Instant.parse("2026-04-10T06:00:00Z"));
        when(authPageImageRepository.findAllByOrderByDisplayOrderAscCreatedAtDescIdDesc())
                .thenReturn(List.of(first, second, third));

        AdminDtos.ActionResponse response = authPageImageService.reorder(
                new AdminDtos.ReorderAuthPageImagesRequest(List.of(8L, 4L, 7L))
        );

        verify(authPageImageRepository).saveAll(anyList());
        assertEquals("Đã cập nhật thứ tự ảnh auth page.", response.message());
        assertEquals(2, first.getDisplayOrder());
        assertEquals(3, second.getDisplayOrder());
        assertEquals(1, third.getDisplayOrder());
    }

    @Test
    void reorderRejectsDuplicateImageIds() {
        AuthPageImage first = image(4L, "https://cdn.example.com/hero.webp", "Hero", "First", 1, 62, 30, Instant.parse("2026-04-18T06:00:00Z"));
        AuthPageImage second = image(7L, "https://cdn.example.com/promo.webp", "Promo", "Second", 2, 50, 50, Instant.parse("2026-04-12T06:00:00Z"));
        when(authPageImageRepository.findAllByOrderByDisplayOrderAscCreatedAtDescIdDesc())
                .thenReturn(List.of(first, second));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authPageImageService.reorder(
                        new AdminDtos.ReorderAuthPageImagesRequest(List.of(4L, 4L))
                )
        );

        assertEquals("Danh sách sắp xếp có ảnh bị lặp. Hãy kéo thả lại.", exception.getMessage());
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
            Integer displayOrder,
            Integer focalPointX,
            Integer focalPointY,
            Instant createdAt
    ) {
        AuthPageImage image = new AuthPageImage();
        image.setId(id);
        image.setImageUrl(imageUrl);
        image.setTitle(title);
        image.setDescription(description);
        image.setDisplayOrder(displayOrder);
        image.setFocalPointX(focalPointX);
        image.setFocalPointY(focalPointY);
        image.setCreatedAt(createdAt);
        return image;
    }
}
