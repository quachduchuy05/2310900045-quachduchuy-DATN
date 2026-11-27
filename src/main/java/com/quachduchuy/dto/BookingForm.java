package com.quachduchuy.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Dữ liệu khi khách bấm "Đặt phòng". */
@Data
public class BookingForm {

    @NotNull
    private Long roomId;

    @NotNull(message = "Chọn ngày nhận phòng")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkIn;

    @NotNull(message = "Chọn ngày trả phòng")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkOut;

    @Min(value = 1, message = "Ít nhất 1 khách")
    private int guests = 1;

    @Size(max = 300, message = "Ghi chú tối đa 300 ký tự")
    private String note;
}
