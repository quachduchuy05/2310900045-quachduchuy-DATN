package com.quachduchuy.repository;

import com.quachduchuy.entity.Booking;
import com.quachduchuy.entity.User;
import java.util.List;
import com.quachduchuy.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTxnRef(String txnRef);

    String WITH_DETAILS = """
            SELECT p FROM Payment p
            JOIN FETCH p.booking b JOIN FETCH b.user u
            JOIN FETCH b.room r JOIN FETCH r.homestay h
            """;

    @Query(WITH_DETAILS + " WHERE u = :user ORDER BY p.createdAt DESC")
    List<Payment> findByGuest(@Param("user") User user);

    @Query(WITH_DETAILS + " WHERE h.host = :host ORDER BY p.createdAt DESC")
    List<Payment> findByHost(@Param("host") User host);

    @Query(WITH_DETAILS + " ORDER BY p.createdAt DESC")
    List<Payment> findAllWithDetails();

    List<Payment> findByBookingOrderByCreatedAtDesc(Booking booking);

    /** Khóa giao dịch khi xử lý kết quả: Return URL và IPN có thể đến cùng lúc. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.txnRef = :txnRef")
    Optional<Payment> findByTxnRefForUpdate(@Param("txnRef") String txnRef);
}
