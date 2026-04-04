package com.filmbe.service;

import com.filmbe.model.FileResource;
import com.filmbe.repository.FileResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class UploadService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private final FileResourceRepository fileResourceRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional
    public String upload(MultipartFile file) {
        validate(file);
        String hash = calculateHash(file);

        return fileResourceRepository.findByHash(hash)
                .map(FileResource::getUrl)
                .orElseGet(() -> saveNewFile(hash, file));
    }

    private String saveNewFile(String hash, MultipartFile file) {
        String url = cloudinaryService.uploadFileToCloudinary(file);
        FileResource fileResource = new FileResource();
        fileResource.setHash(hash);
        fileResource.setUrl(url);
        return fileResourceRepository.save(fileResource).getUrl();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File quá lớn. Kích thước tối đa là 50MB.");
        }
    }

    private String calculateHash(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể đọc file tải lên.");
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể xử lý file tải lên.");
        }
    }
}
