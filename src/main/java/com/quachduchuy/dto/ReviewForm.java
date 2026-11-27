package com.quachduchuy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Form đánh giá sau khi ở. */
@Data
public class ReviewForm {

    @Min(value = 1, message = "Chọn từ 1 đến 5 sao")
    @Max(value = 5, message = "Chọn từ 1 đến 5 sao")
    private int rating;

    @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự")
    private String comment;
}
