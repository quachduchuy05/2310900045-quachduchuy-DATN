package com.quachduchuy.service;

import com.quachduchuy.entity.*;
import com.quachduchuy.exception.BusinessException;
import com.quachduchuy.exception.PaymentException;
import com.quachduchuy.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Nghiệp vụ thanh toán: tạo giao dịch và xử lý kết quả VNPay trả về. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final VnPayService vnPayService;

    /**
     * Tạo bản ghi Payment và trả về URL để chuyển khách đi thanh toán.
     * Nếu chưa cấu hình VNPay thật → trả về trang cổng giả lập trong website.
     */
    @Transactional
    public String createPaymentUrl(User user, Long bookingId, String clientIp) {
        Booking booking = bookingService.getOwnedBooking(user, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("Đơn này không ở trạng thái chờ thanh toán");
        }
        if (LocalDateTime.now().isAfter(bookingService.paymentDeadline(booking))) {
            throw new BusinessException("Đơn đã quá hạn thanh toán, vui lòng đặt lại");
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalPrice());
        // Mã giao dịch phải duy nhất mỗi lần thanh toán: mã đơn + thời gian
        payment.setTxnRef(booking.getId() + "_" + System.currentTimeMillis());
        paymentRepository.save(payment);

        Map<String, String> params = vnPayService.buildPaymentParams(
                payment.getTxnRef(), payment.getAmount(),
                "Thanh toan don dat phong " + booking.getId(), clientIp,
                bookingService.paymentDeadline(booking)); // hạn VNPay trùng hạn giữ phòng

        if (!vnPayService.isConfigured()) {
            return "/payment/demo-gateway?txnRef=" + payment.getTxnRef();
        }
        return vnPayService.buildPaymentUrl(params);
    }

    /** Kết quả xử lý: alreadyProcessed = true nếu giao dịch đã được ghi nhận từ trước. */
    public record PaymentResult(Payment payment, boolean alreadyProcessed) {
    }

    /**
     * Xử lý kết quả VNPay gửi về (dùng chung cho Return URL và IPN).
     * Hàm này chạy được nhiều lần cho cùng một giao dịch mà không bị cộng trùng.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResult handleVnPayResult(Map<String, String> params) {
        if (!vnPayService.verifySignature(params)) {
            throw new PaymentException("97", "Chữ ký không hợp lệ, dữ liệu thanh toán có thể đã bị sửa");
        }

        Payment payment = paymentRepository.findByTxnRefForUpdate(params.get("vnp_TxnRef"))
                .orElseThrow(() -> new PaymentException("01", "Không tìm thấy giao dịch"));

        // Số tiền VNPay trả về phải khớp số tiền đã lưu
        if (!amountMatches(params.get("vnp_Amount"), payment.getAmount())) {
            throw new PaymentException("04", "Số tiền thanh toán không khớp");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return new PaymentResult(payment, true); // đã xử lý rồi (ví dụ IPN đến trước Return URL)
        }

        boolean success = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.getOrDefault("vnp_TransactionStatus", "00"));

        payment.setResponseCode(params.get("vnp_ResponseCode"));
        payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        payment.setBankCode(params.get("vnp_BankCode"));

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            confirmBooking(bookingService.lockBooking(payment.getBooking().getId()));
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        return new PaymentResult(payment, false);
    }

    /** vnp_Amount là số tiền nhân 100. */
    private boolean amountMatches(String vnpAmount, BigDecimal expected) {
        if (vnpAmount == null) {
            return false;
        }
        try {
            return new BigDecimal(vnpAmount).compareTo(expected.multiply(BigDecimal.valueOf(100))) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Thanh toán thành công → chuyển đơn sang PAID. */
    private void confirmBooking(Booking booking) {
        switch (booking.getStatus()) {
            case PENDING -> booking.setStatus(BookingStatus.PAID);
            case CANCELLED -> {
                // Đơn đã bị hủy nhưng khách vẫn trả tiền: còn trống thì khôi phục, ngược lại cần hoàn tiền
                Long roomId = booking.getRoom().getId();
                bookingService.lockRoom(roomId);
                if (bookingService.isRoomFree(roomId, booking.getCheckIn(), booking.getCheckOut())) {
                    booking.setStatus(BookingStatus.PAID);
                } else {
                    requireRefund(booking, "Đã thanh toán nhưng phòng không còn trống - CẦN HOÀN TIỀN");
                }
            }
            case PAID, COMPLETED -> requireRefund(booking, "Khách thanh toán trùng - CẦN HOÀN TIỀN");
        }
    }

    private void requireRefund(Booking booking, String reason) {
        booking.setNote(bookingService.appendNote(booking.getNote(), reason));
        log.warn("Booking {}: {}", booking.getId(), reason);
    }

    // ---- Lịch sử thanh toán ----
    public List<Payment> historyOfGuest(User guest) {
        return paymentRepository.findByGuest(guest);
    }

    public List<Payment> historyOfHost(User host) {
        return paymentRepository.findByHost(host);
    }

    public List<Payment> historyAll() {
        return paymentRepository.findAllWithDetails();
    }

    public List<Payment> historyOfBooking(Booking booking) {
        return paymentRepository.findByBookingOrderByCreatedAtDesc(booking);
    }

    /** Lấy giao dịch và đảm bảo nó thuộc đơn của người đang đăng nhập. */
    @Transactional(readOnly = true)
    public Payment getOwnedPayment(User user, String txnRef) {
        Payment payment = paymentRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new BusinessException("Không tìm thấy giao dịch"));
        if (!payment.getBooking().getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền với giao dịch này");
        }
        return payment;
    }

    /**
     * CHỈ DÙNG KHI DEMO (chưa có tài khoản VNPay sandbox):
     * tạo bộ tham số giống hệt VNPay trả về, có chữ ký hợp lệ, để chạy đủ luồng thanh toán.
     */
    @Transactional(readOnly = true)
    public String buildDemoReturnQuery(User user, String txnRef, boolean success) {
        Payment payment = getOwnedPayment(user, txnRef);
        Map<String, String> result = new java.util.TreeMap<>();
        result.put("vnp_TxnRef", txnRef);
        result.put("vnp_Amount", payment.getAmount().multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        result.put("vnp_ResponseCode", success ? "00" : "24"); // 24 = khách hủy giao dịch
        result.put("vnp_TransactionStatus", success ? "00" : "02");
        result.put("vnp_TransactionNo", String.valueOf(System.currentTimeMillis() % 100000000));
        result.put("vnp_BankCode", "DEMO");
        return vnPayService.signAndBuildQuery(result);
    }
}
