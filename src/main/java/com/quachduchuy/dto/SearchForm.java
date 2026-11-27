package com.quachduchuy.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Bộ lọc tìm kiếm homestay; mọi trường đều không bắt buộc. */
@Data
public class SearchForm {

    private String keyword;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkIn;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkOut;

    @Min(value = 1, message = "Số khách ít nhất là 1")
    private Integer guests;

    @Min(value = 0, message = "Giá tối đa không hợp lệ")
    private BigDecimal maxPrice;

    /** newest (mặc định) | priceAsc | priceDesc | rating */
    private String sort;
}
