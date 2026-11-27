package com.quachduchuy.dto;

import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Dữ liệu form thêm/sửa homestay của Host. */
@Data
public class HomestayForm {

    @NotBlank(message = "Vui lòng nhập tên homestay")
    @Size(max = 150, message = "Tên tối đa 150 ký tự")
    private String name;

    @NotBlank(message = "Vui lòng nhập thành phố")
    @Size(max = 100, message = "Thành phố tối đa 100 ký tự")
    private String city;

    @NotBlank(message = "Vui lòng nhập địa chỉ")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @Size(max = 10000, message = "Mô tả tối đa 10000 ký tự")
    private String description;
    @Size(max = 255, message = "Link ảnh tối đa 255 ký tự")
    private String imageUrl;
    /** Ảnh tải lên từ máy (ưu tiên hơn link ảnh). */
    private MultipartFile imageFile;
    @Size(max = 500, message = "Tiện ích tối đa 500 ký tự")
    private String amenities;
}
