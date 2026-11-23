package com.quachduchuy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Dữ liệu form thêm/sửa phòng. */
@Data
public class RoomForm {

    @NotBlank(message = "Vui lòng nhập tên phòng")
    private String name;

    @NotNull(message = "Vui lòng nhập giá")
    @Min(value = 10000, message = "Giá tối thiểu 10.000đ")
    private BigDecimal pricePerNight;

    @Min(value = 1, message = "Sức chứa tối thiểu 1 người")
    private int capacity = 2;

    private String description;
    private String imageUrl;
    private boolean available = true;
}
