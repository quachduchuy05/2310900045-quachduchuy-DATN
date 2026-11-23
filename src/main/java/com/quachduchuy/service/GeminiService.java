package com.quachduchuy.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gọi Google Gemini API (REST) để sinh văn bản.
 * Tài liệu: https://ai.google.dev/api/generate-content
 */
@Slf4j
@Service
public class GeminiService {

    private final RestClient restClient = RestClient.create();

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.url}")
    private String baseUrl;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("YOUR_");
    }

    /**
     * Gửi câu lệnh (prompt) cho Gemini và nhận câu trả lời.
     * Trả về Optional.empty() nếu chưa có API key hoặc gọi lỗi, để nơi gọi dùng phương án dự phòng.
     */
    public Optional<String> generate(String prompt) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        // Cấu trúc body theo đúng định dạng Gemini yêu cầu
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.7, "maxOutputTokens", 2048)
        );

        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl + model + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            // Câu trả lời nằm ở: candidates[0].content.parts[0].text
            String text = response.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText("");
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.error("Gọi Gemini thất bại: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
