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
                authPageImageRepository.findAllByOrderByDisplayOrderAscCreatedAtDescIdDesc()
                        .stream()
                        .map(this::toAdminItem)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public CatalogDtos.AuthPageImageListResponse listPublic() {
        return new CatalogDtos.AuthPageImageListResponse(
                authPageImageRepository.findAllByOrderByDisplayOrderAscCreatedAtDescIdDesc()
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
                request.displayOrder(),
                request.focalPointX(),
                request.focalPointY(),
                resolveCreateDisplayOrder(request.displayOrder())
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
                request.displayOrder(),
                request.focalPointX(),
                request.focalPointY(),
                currentDisplayOrder(image)
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
                image.getDisplayOrder(),
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
                image.getDisplayOrder(),
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
            Integer displayOrder,
            Integer focalPointX,
            Integer focalPointY,
            int fallbackDisplayOrder
    ) {
        image.setImageUrl(imageUrl.trim());
        image.setTitle(blankToNull(title));
        image.setDescription(blankToNull(description));
        image.setDisplayOrder(normalizeDisplayOrder(displayOrder, fallbackDisplayOrder));
        image.setFocalPointX(normalizePosition(focalPointX));
        image.setFocalPointY(normalizePosition(focalPointY));
    }

    private int currentDisplayOrder(AuthPageImage image) {
        Integer displayOrder = image.getDisplayOrder();
        return displayOrder == null ? 1 : Math.max(1, displayOrder);
    }

    private int nextDisplayOrder() {
        return authPageImageRepository.findFirstByOrderByDisplayOrderDescIdDesc()
                .map(this::currentDisplayOrder)
                .orElse(0) + 1;
    }

    private int resolveCreateDisplayOrder(Integer requestedDisplayOrder) {
        if (requestedDisplayOrder != null) {
            return requestedDisplayOrder;
        }
        return nextDisplayOrder();
    }

    private Integer normalizeDisplayOrder(Integer value, int fallbackValue) {
        if (value == null) {
            return Math.max(1, fallbackValue);
        }
        return Math.max(1, value);
    }

    private Integer normalizePosition(Integer value) {
        if (value == null) {
            return 50;
        }
        return Math.max(0, Math.min(value, 100));
    }
}
