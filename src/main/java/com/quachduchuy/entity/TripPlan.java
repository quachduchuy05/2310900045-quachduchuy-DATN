package com.quachduchuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Bảng trip_plans: lưu lại các lịch trình AI đã gợi ý cho người dùng để xem lại. */
@Entity
@Table(name = "trip_plans")
@Getter
@Setter
@NoArgsConstructor
public class TripPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String destination;

    private int days;

    @Column(precision = 14, scale = 0)
    private BigDecimal budget;

    @Column(length = 500)
    private String interests;

    @Column(columnDefinition = "TEXT")
    private String content; // nội dung lịch trình AI trả về

    private LocalDateTime createdAt = LocalDateTime.now();
}
