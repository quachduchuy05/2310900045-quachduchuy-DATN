package com.quachduchuy.repository;

import com.quachduchuy.entity.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import com.quachduchuy.entity.Homestay;
import com.quachduchuy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HomestayRepository extends JpaRepository<Homestay, Long> {

    List<Homestay> findByHost(User host);

    List<Homestay> findByActiveTrueOrderByCreatedAtDesc();

    /**
     * Tìm theo từ khóa và bộ lọc phòng. Khi filterRooms = true, homestay phải còn ít nhất một phòng
     * đủ chỗ, đúng tầm giá và không có đơn còn hiệu lực giao với [checkIn, checkOut).
     */
    @Query("""
            SELECT h FROM Homestay h
            WHERE h.active = true
              AND (LOWER(h.city) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(h.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:filterRooms = false OR EXISTS (
                    SELECT r.id FROM Room r
                    WHERE r.homestay = h AND r.available = true
                      AND r.capacity >= :guests AND r.pricePerNight <= :maxPrice
                      AND NOT EXISTS (
                            SELECT b.id FROM Booking b
                            WHERE b.room = r AND b.status IN :activeStatuses
                              AND b.checkIn < :checkOut AND :checkIn < b.checkOut)))
            ORDER BY h.createdAt DESC
            """)
    List<Homestay> filter(@Param("keyword") String keyword,
                          @Param("filterRooms") boolean filterRooms,
                          @Param("guests") int guests,
                          @Param("maxPrice") BigDecimal maxPrice,
                          @Param("checkIn") LocalDate checkIn,
                          @Param("checkOut") LocalDate checkOut,
                          @Param("activeStatuses") Collection<BookingStatus> activeStatuses);

    /** Danh sách thành phố không trùng lặp (để hiện gợi ý ở ô tìm kiếm). */
    @Query("SELECT DISTINCT h.city FROM Homestay h WHERE h.active = true ORDER BY h.city")
    List<String> findAllCities();
}
