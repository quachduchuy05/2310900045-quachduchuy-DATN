package com.quachduchuy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Dữ liệu form thêm/sửa homestay của Host. */
@Data
public class HomestayForm {

    @NotBlank(message = "Vui lòng nhập tên homestay")
    private String name;

    @NotBlank(message = "Vui lòng nhập thành phố")
    private String city;

    @NotBlank(message = "Vui lòng nhập địa chỉ")
    private String address;

    private String description;
    private String imageUrl;
    private String amenities;
}
