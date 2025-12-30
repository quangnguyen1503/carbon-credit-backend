package com.example.carbon_credit.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PinataService {

    @Value("${pinata.api-key}")
    private String apiKey;

    @Value("${pinata.secret}")
    private String secret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Upload file (image hoặc PDF) lên IPFS
     */
    public String uploadFileToIPFS(MultipartFile file, String baseName) throws IOException {
        if (file.isEmpty()) {
            log.warn("Empty file provided: {}", baseName);
            return null;
        }
        String fileName = baseName + "-" + UUID.randomUUID() + "." + getFileExtension(file.getOriginalFilename());
        return uploadGenericToIPFS(file.getBytes(), fileName, "file");
    }

    /**
     * Upload JSON metadata lên IPFS
     */
    public String uploadJsonToIPFS(Map<String, Object> metadata, String baseName) {
        try {
            String jsonString = objectMapper.writeValueAsString(metadata);

            // Prepare headers: Basic Auth
            HttpHeaders headers = prepareAuthHeaders();

            HttpEntity<String> requestEntity = new HttpEntity<>(jsonString, headers);

            // Call Pinata JSON API
            ResponseEntity<PinataResponse> response = restTemplate.postForEntity(
                    "https://api.pinata.cloud/pinning/pinJSONToIPFS",
                    requestEntity,
                    PinataResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                String ipfsHash = response.getBody().getIpfsHash();
                String uri = "ipfs://" + ipfsHash;
                log.info("JSON uploaded to IPFS: {} -> {}", baseName, uri);
                return uri;
            } else {
                log.error("JSON upload failed: Status={}, Body={}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Pinata JSON upload error: {}", e.getMessage(), e);
        }
        return null;
    }

    // Helper: Prepare common auth headers
    private HttpHeaders prepareAuthHeaders() {
        String credentials = apiKey + ":" + secret;
        String encodedAuth = Base64.getEncoder().encodeToString(credentials.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }

    // Helper: Get file extension
    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return "bin"; // Default
    }

    // Helper: Generic file upload
    private String uploadGenericToIPFS(byte[] fileBytes, String fileName, String formKey) throws IOException {
        HttpHeaders headers = prepareAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(formKey, new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() { return fileName; }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<PinataResponse> response = restTemplate.postForEntity(
                "https://api.pinata.cloud/pinning/pinFileToIPFS",
                requestEntity,
                PinataResponse.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            String ipfsHash = response.getBody().getIpfsHash();
            String gatewayUrl = "https://gateway.pinata.cloud/ipfs/" + ipfsHash;
            log.info("File uploaded to IPFS: {} -> {}", fileName, gatewayUrl);
            return gatewayUrl;
        } else {
            log.error("File upload failed: Status={}, Body={}", response.getStatusCode(), response.getBody());
        }
        return null;
    }

    // Inner class for Pinata response (cleaner than Map)
    public static class PinataResponse {
        private String IpfsHash;

        public String getIpfsHash() { return IpfsHash; }
        public void setIpfsHash(String ipfsHash) { IpfsHash = ipfsHash; }
    }
}