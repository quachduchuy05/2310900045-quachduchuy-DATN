package com.quachduchuy.repository;

import com.quachduchuy.entity.Homestay;
import com.quachduchuy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HomestayRepository extends JpaRepository<Homestay, Long> {

    List<Homestay> findByHost(User host);

    List<Homestay> findByActiveTrueOrderByCreatedAtDesc();

    /** Tìm homestay đang hoạt động theo từ khóa (tên hoặc thành phố). */
    @Query("""
            SELECT h FROM Homestay h
            WHERE h.active = true
              AND (LOWER(h.city) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(h.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY h.createdAt DESC
            """)
    List<Homestay> search(@Param("keyword") String keyword);

    /** Danh sách thành phố không trùng lặp (để hiện gợi ý ở ô tìm kiếm). */
    @Query("SELECT DISTINCT h.city FROM Homestay h WHERE h.active = true ORDER BY h.city")
    List<String> findAllCities();
}
