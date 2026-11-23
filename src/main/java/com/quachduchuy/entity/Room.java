package com.quachduchuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Bảng rooms: phòng cụ thể bên trong một homestay, là đối tượng được đặt. */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name; // ví dụ: "Phòng đôi view đồi"

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal pricePerNight; // giá VNĐ / đêm

    @Column(nullable = false)
    private int capacity; // số khách tối đa

    @Column(columnDefinition = "TEXT")
    private String description;

    private String imageUrl;

    private boolean available = true; // Host có thể tạm ngưng nhận khách

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homestay_id", nullable = false)
    private Homestay homestay;
}
