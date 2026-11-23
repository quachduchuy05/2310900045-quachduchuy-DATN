package com.quachduchuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Bảng payments: mỗi lần khách bấm thanh toán VNPay tạo ra một bản ghi. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, unique = true, length = 50)
    private String txnRef; // mã giao dịch gửi sang VNPay (vnp_TxnRef)

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(length = 50)
    private String vnpTransactionNo; // mã giao dịch phía VNPay trả về

    @Column(length = 10)
    private String responseCode; // "00" = thành công

    @Column(length = 30)
    private String bankCode;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime paidAt;
}
