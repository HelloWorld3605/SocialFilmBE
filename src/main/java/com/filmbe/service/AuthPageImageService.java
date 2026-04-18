package com.filmbe.service;

import com.filmbe.dto.AdminDtos;
import com.filmbe.dto.CatalogDtos;
import com.filmbe.model.AuthPageImage;
import com.filmbe.repository.AuthPageImageRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthPageImageService {

    private final AuthPageImageRepository authPageImageRepository;

    @Transactional(readOnly = true)
    public AdminDtos.AuthPageImageListResponse listAdmin() {
        return new AdminDtos.AuthPageImageListResponse(
                authPageImageRepository.findAllByOrderByCreatedAtDescIdDesc()
                        .stream()
                        .map(this::toAdminItem)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public CatalogDtos.AuthPageImageListResponse listPublic() {
        return new CatalogDtos.AuthPageImageListResponse(
                authPageImageRepository.findAllByOrderByCreatedAtDescIdDesc()
                        .stream()
                        .map(this::toCatalogItem)
                        .toList()
        );
    }

    @Transactional
    public AdminDtos.AuthPageImageItem create(AdminDtos.CreateAuthPageImageRequest request) {
        AuthPageImage image = new AuthPageImage();
        applyValues(
                image,
                request.imageUrl(),
                request.title(),
                request.description(),
                request.focalPointX(),
                request.focalPointY()
        );
        return toAdminItem(authPageImageRepository.save(image));
    }

    @Transactional
    public AdminDtos.AuthPageImageItem update(Long imageId, AdminDtos.UpdateAuthPageImageRequest request) {
        AuthPageImage image = authPageImageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy ảnh auth page."));

        applyValues(
                image,
                request.imageUrl(),
                request.title(),
                request.description(),
                request.focalPointX(),
                request.focalPointY()
        );

        return toAdminItem(authPageImageRepository.save(image));
    }

    @Transactional
    public AdminDtos.ActionResponse delete(Long imageId) {
        AuthPageImage image = authPageImageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy ảnh auth page."));

        authPageImageRepository.delete(image);

        return new AdminDtos.ActionResponse("Đã xóa ảnh khỏi giao diện đăng nhập/đăng ký.");
    }

    private AdminDtos.AuthPageImageItem toAdminItem(AuthPageImage image) {
        return new AdminDtos.AuthPageImageItem(
                image.getId(),
                image.getImageUrl(),
                image.getTitle(),
                image.getDescription(),
                image.getFocalPointX(),
                image.getFocalPointY(),
                image.getCreatedAt()
        );
    }

    private CatalogDtos.AuthPageImageItem toCatalogItem(AuthPageImage image) {
        return new CatalogDtos.AuthPageImageItem(
                image.getId(),
                image.getImageUrl(),
                image.getTitle(),
                image.getDescription(),
                image.getFocalPointX(),
                image.getFocalPointY(),
                image.getCreatedAt()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void applyValues(
            AuthPageImage image,
            String imageUrl,
            String title,
            String description,
            Integer focalPointX,
            Integer focalPointY
    ) {
        image.setImageUrl(imageUrl.trim());
        image.setTitle(blankToNull(title));
        image.setDescription(blankToNull(description));
        image.setFocalPointX(normalizePosition(focalPointX));
        image.setFocalPointY(normalizePosition(focalPointY));
    }

    private Integer normalizePosition(Integer value) {
        if (value == null) {
            return 50;
        }
        return Math.max(0, Math.min(value, 100));
    }
}
