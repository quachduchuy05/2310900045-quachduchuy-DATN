package com.quachduchuy.repository;

import com.quachduchuy.entity.Homestay;
import com.quachduchuy.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByHomestay(Homestay homestay);

    /**
     * Lấy phòng và KHÓA dòng này trong DB (SELECT ... FOR UPDATE) cho tới khi transaction kết thúc.
     * Nhờ vậy 2 người bấm đặt cùng một phòng cùng lúc sẽ phải xếp hàng, không thể cùng đặt thành công.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    /** Dùng cho AI: lấy các phòng còn mở theo thành phố, sức chứa và giá tối đa. */
    @Query("""
            SELECT r FROM Room r JOIN FETCH r.homestay h
            WHERE h.active = true AND r.available = true
              AND LOWER(h.city) LIKE LOWER(CONCAT('%', :city, '%'))
              AND r.capacity >= :guests
              AND r.pricePerNight <= :maxPrice
            ORDER BY r.pricePerNight ASC
            """)
    List<Room> findCandidates(@Param("city") String city,
                              @Param("guests") int guests,
                              @Param("maxPrice") BigDecimal maxPrice);
}
