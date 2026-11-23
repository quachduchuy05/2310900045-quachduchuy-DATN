package com.quachduchuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Bảng bookings: một lần khách đặt một phòng trong khoảng ngày [checkIn, checkOut). */
@Entity
@Table(name = "bookings", indexes = {
        // index giúp truy vấn kiểm tra trùng lịch chạy nhanh
        @Index(name = "idx_booking_room_dates", columnList = "room_id, checkIn, checkOut")
})
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private LocalDate checkIn;

    @Column(nullable = false)
    private LocalDate checkOut; // ngày trả phòng (đêm cuối là checkOut - 1)

    private int guests;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(length = 500)
    private String note;

    private LocalDateTime createdAt = LocalDateTime.now();

    /** Số đêm lưu trú. */
    public long getNights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }
}
