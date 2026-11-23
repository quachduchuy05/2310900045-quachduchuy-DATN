package com.quachduchuy.service;

import com.quachduchuy.dto.AiSuggestion;
import com.quachduchuy.dto.TripPlanRequest;
import com.quachduchuy.entity.Room;
import com.quachduchuy.entity.TripPlan;
import com.quachduchuy.entity.User;
import com.quachduchuy.repository.RoomRepository;
import com.quachduchuy.repository.TripPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * TRỢ LÝ AI: gợi ý phòng + lập lịch trình du lịch cá nhân hóa.
 *
 * Ý tưởng chính:
 *  1. Lọc trước trong CSDL những phòng THẬT phù hợp (đúng thành phố, đủ chỗ, giá hợp ngân sách).
 *  2. Đưa danh sách phòng đó vào prompt, nhờ Gemini chọn phòng và viết lịch trình.
 *  → AI chỉ gợi ý phòng có thật trên Staygo, không "bịa" ra khách sạn khác.
 */
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    /** Tỉ lệ ngân sách dành cho chỗ ở (phần còn lại cho ăn uống, đi lại, vé tham quan). */
    private static final BigDecimal ACCOMMODATION_RATIO = new BigDecimal("0.5");
    private static final int MAX_ROOMS_IN_PROMPT = 5;

    private final RoomRepository roomRepository;
    private final TripPlanRepository tripPlanRepository;
    private final GeminiService geminiService;

    @Transactional
    public AiSuggestion planTrip(User user, TripPlanRequest req) {
        int nights = Math.max(1, req.getDays() - 1);

        // Giá tối đa / đêm = (ngân sách × 50%) / số đêm
        BigDecimal maxPricePerNight = req.getBudget()
                .multiply(ACCOMMODATION_RATIO)
                .divide(BigDecimal.valueOf(nights), 0, RoundingMode.DOWN);

        List<Room> rooms = roomRepository
                .findCandidates(req.getDestination().trim(), req.getGuests(), maxPricePerNight)
                .stream().limit(MAX_ROOMS_IN_PROMPT).toList();

        String prompt = buildPrompt(req, nights, maxPricePerNight, rooms);
        String aiText = geminiService.generate(prompt).orElse(null);
        boolean fromAi = aiText != null;
        String content = fromAi ? aiText : buildFallbackPlan(req, nights, rooms);

        saveHistory(user, req, content);
        return new AiSuggestion(content, rooms, fromAi);
    }

    public List<TripPlan> history(User user) {
        return tripPlanRepository.findTop10ByUserOrderByCreatedAtDesc(user);
    }

    /** Soạn câu lệnh gửi cho Gemini. Prompt càng rõ ràng thì câu trả lời càng tốt. */
    private String buildPrompt(TripPlanRequest req, int nights, BigDecimal maxPrice, List<Room> rooms) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý du lịch của website đặt phòng Staygo tại Việt Nam.\n");
        sb.append("Hãy lập lịch trình du lịch chi tiết bằng tiếng Việt, định dạng Markdown.\n\n");
        sb.append("THÔNG TIN KHÁCH:\n");
        sb.append("- Điểm đến: ").append(req.getDestination()).append("\n");
        sb.append("- Số ngày: ").append(req.getDays()).append(" ngày ").append(nights).append(" đêm\n");
        sb.append("- Số người: ").append(req.getGuests()).append("\n");
        sb.append("- Tổng ngân sách: ").append(money(req.getBudget())).append("\n");
        sb.append("- Sở thích: ").append(blankToDefault(req.getInterests(), "không nêu cụ thể")).append("\n\n");

        sb.append("DANH SÁCH PHÒNG CÒN TRÊN STAYGO (giá ≤ ").append(money(maxPrice)).append("/đêm):\n");
        if (rooms.isEmpty()) {
            sb.append("(Không có phòng phù hợp. Hãy gợi ý khách tăng ngân sách hoặc chọn điểm đến khác.)\n");
        } else {
            for (Room r : rooms) {
                sb.append("- ").append(r.getName()).append(" tại ").append(r.getHomestay().getName())
                  .append(" (").append(r.getHomestay().getAddress()).append("), ")
                  .append(money(r.getPricePerNight())).append("/đêm, tối đa ")
                  .append(r.getCapacity()).append(" khách, tiện ích: ")
                  .append(blankToDefault(r.getHomestay().getAmenities(), "cơ bản")).append("\n");
            }
        }

        sb.append("\nYÊU CẦU:\n");
        sb.append("1. Mục \"## Gợi ý chỗ ở\": chọn 1-2 phòng TRONG DANH SÁCH TRÊN phù hợp nhất và giải thích ngắn. ");
        sb.append("Tuyệt đối không gợi ý khách sạn ngoài danh sách.\n");
        sb.append("2. Mục \"## Lịch trình\": chia theo từng ngày (### Ngày 1, ### Ngày 2...), mỗi ngày có sáng/chiều/tối, ");
        sb.append("địa điểm cụ thể, món ăn nên thử, phù hợp sở thích.\n");
        sb.append("3. Mục \"## Dự trù chi phí\": bảng Markdown gồm chỗ ở, ăn uống, di chuyển, vé tham quan, tổng; ");
        sb.append("tổng không vượt ngân sách.\n");
        sb.append("4. Mục \"## Lưu ý\": 3-5 mẹo ngắn (thời tiết, trang phục, đặt phòng sớm...).\n");
        sb.append("Viết ngắn gọn, thân thiện.");
        return sb.toString();
    }

    /** Lịch trình dự phòng khi chưa cấu hình Gemini hoặc gọi lỗi, để website vẫn hoạt động. */
    private String buildFallbackPlan(TripPlanRequest req, int nights, List<Room> rooms) {
        StringBuilder sb = new StringBuilder();
        sb.append("> ⚠️ Chưa kết nối được Gemini AI, đây là lịch trình mẫu. ")
          .append("Thêm `gemini.api-key` trong application.properties để nhận gợi ý cá nhân hóa.\n\n");

        sb.append("## Gợi ý chỗ ở\n");
        if (rooms.isEmpty()) {
            sb.append("Chưa có phòng phù hợp ngân sách tại ").append(req.getDestination())
              .append(". Bạn thử tăng ngân sách hoặc chọn điểm đến khác nhé.\n\n");
        } else {
            Room best = rooms.get(0);
            sb.append("- **").append(best.getName()).append("** tại ").append(best.getHomestay().getName())
              .append(": ").append(money(best.getPricePerNight())).append("/đêm, rẻ nhất trong các phòng phù hợp.\n\n");
        }

        sb.append("## Lịch trình\n");
        for (int day = 1; day <= req.getDays(); day++) {
            sb.append("### Ngày ").append(day).append("\n");
            if (day == 1) {
                sb.append("- **Sáng:** Di chuyển tới ").append(req.getDestination()).append(", nhận phòng.\n");
            } else {
                sb.append("- **Sáng:** Tham quan điểm nổi tiếng của ").append(req.getDestination()).append(".\n");
            }
            sb.append("- **Chiều:** Khám phá theo sở thích: ")
              .append(blankToDefault(req.getInterests(), "dạo phố, chụp ảnh")).append(".\n");
            sb.append(day == req.getDays()
                    ? "- **Tối:** Mua quà, trả phòng và về.\n\n"
                    : "- **Tối:** Thưởng thức ẩm thực địa phương ở chợ đêm.\n\n");
        }

        BigDecimal stay = rooms.isEmpty() ? BigDecimal.ZERO
                : rooms.get(0).getPricePerNight().multiply(BigDecimal.valueOf(nights));
        sb.append("## Dự trù chi phí\n");
        sb.append("| Khoản | Chi phí |\n|---|---|\n");
        sb.append("| Chỗ ở (").append(nights).append(" đêm) | ").append(money(stay)).append(" |\n");
        sb.append("| Ăn uống, đi lại, vé tham quan | ").append(money(req.getBudget().subtract(stay))).append(" |\n");
        sb.append("| **Tổng ngân sách** | **").append(money(req.getBudget())).append("** |\n");
        return sb.toString();
    }

    private void saveHistory(User user, TripPlanRequest req, String content) {
        TripPlan plan = new TripPlan();
        plan.setUser(user);
        plan.setDestination(req.getDestination());
        plan.setDays(req.getDays());
        plan.setBudget(req.getBudget());
        plan.setInterests(req.getInterests());
        plan.setContent(content);
        tripPlanRepository.save(plan);
    }

    private String money(BigDecimal amount) {
        return NumberFormat.getInstance(Locale.of("vi", "VN")).format(amount) + "đ";
    }

    private String blankToDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }
}
