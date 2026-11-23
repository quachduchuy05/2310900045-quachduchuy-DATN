package com.quachduchuy.dto;

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
    private String destination;

    @Min(value = 1, message = "Ít nhất 1 ngày")
    @Max(value = 14, message = "Tối đa 14 ngày")
    private int days = 3;

    @Min(value = 1, message = "Ít nhất 1 người")
    private int guests = 2;

    @NotNull(message = "Nhập ngân sách")
    @Min(value = 100000, message = "Ngân sách tối thiểu 100.000đ")
    private BigDecimal budget;

    private String interests; // ví dụ: "ẩm thực, chụp ảnh, thiên nhiên"
}
