package com.quachduchuy.dto;

import com.quachduchuy.entity.Room;

import java.util.List;

/**
 * Kết quả AI trả về cho giao diện.
 * @param content       nội dung lịch trình (dạng Markdown đơn giản)
 * @param suggestedRooms các phòng thật trong hệ thống phù hợp ngân sách
 * @param fromAi        true nếu do Gemini tạo; false nếu là gợi ý dự phòng khi chưa có API key
 */
public record AiSuggestion(String content, List<Room> suggestedRooms, boolean fromAi) {
}
