package com.quachduchuy.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
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

    private final RestClient restClient = createClient();

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.url}")
    private String baseUrl;

    /** Đặt thời gian chờ để trang không treo khi Gemini phản hồi chậm. */
    private static RestClient createClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory).build();
    }

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
                "generationConfig", Map.of("temperature", 0.7, "maxOutputTokens", 8192)
        );

        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl + model + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            // Câu trả lời nằm ở candidates[0].content.parts[*].text (bỏ phần "suy nghĩ" của model)
            StringBuilder answer = new StringBuilder();
            for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) {
                    answer.append(part.path("text").asText(""));
                }
            }
            String text = answer.toString();
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.error("Gọi Gemini thất bại: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
