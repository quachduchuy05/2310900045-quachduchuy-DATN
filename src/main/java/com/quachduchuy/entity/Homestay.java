package com.quachduchuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Bảng homestays: một homestay thuộc về một Host và có nhiều phòng. */
@Entity
@Table(name = "homestays")
@Getter
@Setter
@NoArgsConstructor
public class Homestay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String city; // dùng để tìm kiếm, ví dụ: "Đà Lạt"

    @Column(nullable = false)
    private String address;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String imageUrl;

    @Column(length = 500)
    private String amenities; // tiện ích, ví dụ: "Wifi, Bể bơi, Bữa sáng"

    private boolean active = true; // Admin có thể ẩn homestay vi phạm

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @OneToMany(mappedBy = "homestay", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Room> rooms = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();

    /** Giá thấp nhất trong các phòng (hiển thị "Từ ...đ / đêm"). Trả về null nếu chưa có phòng. */
    public BigDecimal getMinPrice() {
        return rooms.stream().map(Room::getPricePerNight).min(BigDecimal::compareTo).orElse(null);
    }
}
