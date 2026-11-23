package com.quachduchuy.service;

import com.quachduchuy.entity.*;
import com.quachduchuy.exception.BusinessException;
import com.quachduchuy.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
                "Thanh toan don dat phong " + booking.getId(), clientIp);

        if (!vnPayService.isConfigured()) {
            return "/payment/demo-gateway?txnRef=" + payment.getTxnRef();
        }
        return vnPayService.buildPaymentUrl(params);
    }

    /**
     * Xử lý kết quả VNPay gửi về (dùng chung cho Return URL và IPN).
     * Hàm này chạy được nhiều lần cho cùng một giao dịch mà không bị cộng trùng.
     */
    @Transactional
    public Payment handleVnPayResult(Map<String, String> params) {
        if (!vnPayService.verifySignature(params)) {
            throw new BusinessException("Chữ ký không hợp lệ, dữ liệu thanh toán có thể đã bị sửa");
        }

        Payment payment = paymentRepository.findByTxnRef(params.get("vnp_TxnRef"))
                .orElseThrow(() -> new BusinessException("Không tìm thấy giao dịch"));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return payment; // đã xử lý rồi (ví dụ IPN đến trước Return URL)
        }

        // Số tiền VNPay trả về phải khớp số tiền đã lưu
        BigDecimal paidAmount = new BigDecimal(params.get("vnp_Amount")).divide(BigDecimal.valueOf(100));
        boolean amountOk = paidAmount.compareTo(payment.getAmount()) == 0;
        boolean success = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.getOrDefault("vnp_TransactionStatus", "00"))
                && amountOk;

        payment.setResponseCode(params.get("vnp_ResponseCode"));
        payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        payment.setBankCode(params.get("vnp_BankCode"));

        Booking booking = payment.getBooking();
        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            confirmBooking(booking);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        return payment;
    }

    /** Thanh toán thành công → chuyển đơn sang PAID. */
    private void confirmBooking(Booking booking) {
        if (booking.getStatus() == BookingStatus.PENDING) {
            booking.setStatus(BookingStatus.PAID);
            return;
        }
        // Trường hợp hiếm: đơn đã bị tự hủy vì quá hạn nhưng khách vẫn trả tiền.
        // Nếu phòng vẫn trống thì khôi phục đơn, ngược lại ghi chú để Admin hoàn tiền.
        if (booking.getStatus() == BookingStatus.CANCELLED
                && bookingService.isRoomFree(booking.getRoom().getId(), booking.getCheckIn(), booking.getCheckOut())) {
            booking.setStatus(BookingStatus.PAID);
        } else {
            booking.setNote("Đã thanh toán nhưng phòng không còn trống - CẦN HOÀN TIỀN");
            log.warn("Booking {} đã thanh toán nhưng không thể xác nhận, cần hoàn tiền", booking.getId());
        }
    }

    public Payment getByTxnRef(String txnRef) {
        return paymentRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new BusinessException("Không tìm thấy giao dịch"));
    }

    /**
     * CHỈ DÙNG KHI DEMO (chưa có tài khoản VNPay sandbox):
     * tạo bộ tham số giống hệt VNPay trả về, có chữ ký hợp lệ, để chạy đủ luồng thanh toán.
     */
    public String buildDemoReturnQuery(String txnRef, boolean success) {
        Payment payment = getByTxnRef(txnRef);
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
