package com.quachduchuy.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Thông tin khách nhập để AI lập lịch trình và gợi ý phòng. */
@Data
public class TripPlanRequest {

    @NotBlank(message = "Bạn muốn đi đâu?")
    @Size(max = 100, message = "Điểm đến tối đa 100 ký tự")
    private String destination;

    @Min(value = 1, message = "Ít nhất 1 ngày")
    @Max(value = 14, message = "Tối đa 14 ngày")
    private int days = 3;

    @Min(value = 1, message = "Ít nhất 1 người")
    @Max(value = 50, message = "Tối đa 50 người")
    private int guests = 2;

    @NotNull(message = "Nhập ngân sách")
    @Min(value = 100000, message = "Ngân sách tối thiểu 100.000đ")
    @Digits(integer = 12, fraction = 0, message = "Ngân sách phải là số nguyên hợp lệ")
    private BigDecimal budget;

    @Size(max = 500, message = "Sở thích tối đa 500 ký tự")
    private String interests; // ví dụ: "ẩm thực, chụp ảnh, thiên nhiên"
}
