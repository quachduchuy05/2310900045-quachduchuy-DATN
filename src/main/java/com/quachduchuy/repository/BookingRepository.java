package com.quachduchuy.repository;

import com.quachduchuy.entity.Booking;
import com.quachduchuy.entity.BookingStatus;
import com.quachduchuy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * ĐẾM SỐ ĐƠN BỊ TRÙNG LỊCH.
     * Hai khoảng ngày [a1, a2) và [b1, b2) giao nhau khi: a1 < b2 VÀ b1 < a2.
     * Ví dụ: đơn cũ 10→12, đơn mới 12→14 KHÔNG trùng (ngày 12 khách cũ trả phòng, khách mới nhận phòng).
     * Chỉ tính các đơn còn hiệu lực (PENDING, PAID).
     */
    @Query("""
            SELECT COUNT(b) FROM Booking b
            WHERE b.room.id = :roomId
              AND b.status IN :activeStatuses
              AND b.checkIn < :checkOut
              AND :checkIn < b.checkOut
            """)
    long countOverlapping(@Param("roomId") Long roomId,
                          @Param("checkIn") LocalDate checkIn,
                          @Param("checkOut") LocalDate checkOut,
                          @Param("activeStatuses") Collection<BookingStatus> activeStatuses);

    /** Các đơn còn hiệu lực của một phòng (để hiện lịch đã kín trên trang chi tiết). */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.room.id = :roomId AND b.status IN :activeStatuses AND b.checkOut >= :fromDate
            ORDER BY b.checkIn
            """)
    List<Booking> findActiveByRoom(@Param("roomId") Long roomId,
                                   @Param("fromDate") LocalDate fromDate,
                                   @Param("activeStatuses") Collection<BookingStatus> activeStatuses);

    @Query("""
            SELECT b FROM Booking b JOIN FETCH b.room r JOIN FETCH r.homestay
            WHERE b.user = :user ORDER BY b.createdAt DESC
            """)
    List<Booking> findByUserWithRoom(@Param("user") User user);

    /** Đơn đặt thuộc các homestay của một Host. */
    @Query("""
            SELECT b FROM Booking b JOIN FETCH b.room r JOIN FETCH r.homestay h JOIN FETCH b.user
            WHERE h.host = :host ORDER BY b.createdAt DESC
            """)
    List<Booking> findByHost(@Param("host") User host);

    @Query("""
            SELECT b FROM Booking b JOIN FETCH b.room r JOIN FETCH r.homestay JOIN FETCH b.user
            ORDER BY b.createdAt DESC
            """)
    List<Booking> findAllWithDetails();

    /** Đơn PENDING tạo trước thời điểm "before" → đã quá hạn thanh toán. */
    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime before);

    long countByStatus(BookingStatus status);

    @Query("SELECT COALESCE(SUM(b.totalPrice), 0) FROM Booking b WHERE b.status IN :statuses")
    BigDecimal sumRevenue(@Param("statuses") Collection<BookingStatus> statuses);
}
