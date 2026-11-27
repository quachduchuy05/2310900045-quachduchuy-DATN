package com.quachduchuy.dto;

import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Dữ liệu form thêm/sửa phòng. */
@Data
public class RoomForm {

    @NotBlank(message = "Vui lòng nhập tên phòng")
    @Size(max = 100, message = "Tên phòng tối đa 100 ký tự")
    private String name;

    @NotNull(message = "Vui lòng nhập giá")
    @Min(value = 10000, message = "Giá tối thiểu 10.000đ")
    @Digits(integer = 9, fraction = 0, message = "Giá phải là số nguyên, tối đa 999.999.999đ")
    private BigDecimal pricePerNight;

    @Min(value = 1, message = "Sức chứa tối thiểu 1 người")
    @Max(value = 50, message = "Sức chứa tối đa 50 người")
    private int capacity = 2;

    @Size(max = 10000, message = "Mô tả tối đa 10000 ký tự")
    private String description;
    @Size(max = 255, message = "Link ảnh tối đa 255 ký tự")
    private String imageUrl;
    /** Ảnh tải lên từ máy (ưu tiên hơn link ảnh). */
    private MultipartFile imageFile;
    private boolean available = true;
}
