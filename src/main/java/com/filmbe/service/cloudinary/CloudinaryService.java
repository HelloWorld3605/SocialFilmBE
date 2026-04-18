package com.filmbe.service.cloudinary;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Service
public class CloudinaryService {

    private final RestClient restClient = RestClient.create();

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    public String uploadFileToCloudinary(MultipartFile file) {
        validateConfiguration();

        long timestamp = Instant.now().getEpochSecond();
        String signature = sha1("timestamp=" + timestamp + apiSecret);
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", buildFileResource(file));
        body.add("api_key", apiKey);
        body.add("timestamp", String.valueOf(timestamp));
        body.add("signature", signature);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("https://api.cloudinary.com/v1_1/{cloudName}/auto/upload", cloudName)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            Object secureUrl = response == null ? null : response.get("secure_url");
            if (!(secureUrl instanceof String url) || url.isBlank()) {
                throw new IllegalStateException("Cloudinary không trả về URL hợp lệ.");
            }
            return url;
        } catch (RestClientException exception) {
            log.error("Cloudinary upload failed", exception);
            throw new IllegalStateException("Không thể upload file lên Cloudinary.");
        }
    }

    private void validateConfiguration() {
        if (isBlank(cloudName) || isBlank(apiKey) || isBlank(apiSecret)) {
            throw new IllegalStateException("Cloudinary chưa được cấu hình đầy đủ.");
        }
    }

    private ByteArrayResource buildFileResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    String originalFilename = file.getOriginalFilename();
                    return originalFilename == null || originalFilename.isBlank()
                            ? "upload-file"
                            : originalFilename;
                }
            };
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể đọc file tải lên.");
        }
    }

    private String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể ký yêu cầu Cloudinary.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
