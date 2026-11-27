package com.quachduchuy;

import com.quachduchuy.dto.BookingForm;
import com.quachduchuy.entity.*;
import com.quachduchuy.exception.BusinessException;
import com.quachduchuy.exception.PaymentException;
import com.quachduchuy.repository.*;
import com.quachduchuy.service.BookingService;
import com.quachduchuy.service.PaymentService;
import com.quachduchuy.service.VnPayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kiểm thử nghiệp vụ đặt phòng và thanh toán trên H2 (profile dev). */
@SpringBootTest
@ActiveProfiles("dev")
class BookingPaymentTests {

    @Autowired BookingService bookingService;
    @Autowired PaymentService paymentService;
    @Autowired VnPayService vnPayService;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoomRepository roomRepository;

    User guest;
    User host;
    User admin;
    Long roomId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        guest = userRepository.findByEmail("user@staygo.vn").orElseThrow();
        host = userRepository.findByEmail("host@staygo.vn").orElseThrow();
        admin = userRepository.findByEmail("admin@staygo.vn").orElseThrow();
        roomId = roomRepository.findAll().get(0).getId();
    }

    private BookingForm form(int fromDay, int toDay) {
        BookingForm f = new BookingForm();
        f.setRoomId(roomId);
        f.setCheckIn(LocalDate.now().plusDays(fromDay));
        f.setCheckOut(LocalDate.now().plusDays(toDay));
        f.setGuests(1);
        return f;
    }

    private Booking book(int fromDay, int toDay) {
        return bookingService.createBooking(guest, form(fromDay, toDay));
    }

    private Booking reload(Booking b) {
        return bookingRepository.findById(b.getId()).orElseThrow();
    }

    /** Tạo giao dịch ở cổng giả lập và trả về tham số "VNPay" đã ký. */
    private Map<String, String> payParams(Booking b, boolean success) {
        String url = paymentService.createPaymentUrl(guest, b.getId(), "127.0.0.1");
        return signedResult(url.substring(url.indexOf('=') + 1), success);
    }

    private Map<String, String> signedResult(String txnRef, boolean success) {
        Map<String, String> params = new HashMap<>();
        for (String pair : paymentService.buildDemoReturnQuery(guest, txnRef, success).split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    @Test
    void rejectsOverlapButAllowsBackToBack() {
        book(10, 12);
        assertThatThrownBy(() -> book(11, 13)).isInstanceOf(BusinessException.class);
        assertThat(book(12, 14).getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void concurrentRequestsCreateOnlyOneBooking() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    book(20, 22);
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> r : results) {
            if (r.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();
        assertThat(succeeded).isEqualTo(1);
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void managerMustFollowStatusLifecycle() {
        Booking b = book(5, 7);
        assertThatThrownBy(() -> bookingService.updateStatusByManager(host, b.getId(), BookingStatus.PAID))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> bookingService.updateStatusByManager(guest, b.getId(), BookingStatus.CANCELLED))
                .isInstanceOf(BusinessException.class);
        bookingService.updateStatusByManager(admin, b.getId(), BookingStatus.PAID);
        // chưa tới ngày nhận phòng
        assertThatThrownBy(() -> bookingService.updateStatusByManager(host, b.getId(), BookingStatus.COMPLETED))
                .isInstanceOf(BusinessException.class);
        bookingService.updateStatusByManager(host, b.getId(), BookingStatus.CANCELLED);
        assertThatThrownBy(() -> bookingService.updateStatusByManager(admin, b.getId(), BookingStatus.PAID))
                .isInstanceOf(BusinessException.class);
        assertThat(reload(b).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void successfulPaymentIsProcessedOnce() {
        Booking b = book(5, 7);
        Map<String, String> params = payParams(b, true);

        assertThat(paymentService.handleVnPayResult(params).alreadyProcessed()).isFalse();
        assertThat(paymentService.handleVnPayResult(params).alreadyProcessed()).isTrue();
        assertThat(reload(b).getStatus()).isEqualTo(BookingStatus.PAID);
        assertThat(reload(b).getNote()).isNull();
    }

    @Test
    void secondSuccessfulPaymentIsFlaggedForRefund() {
        Booking b = book(5, 7);
        Map<String, String> first = payParams(b, true);
        Map<String, String> second = payParams(b, true);

        paymentService.handleVnPayResult(first);
        paymentService.handleVnPayResult(second);

        assertThat(reload(b).getNote()).contains("thanh toán trùng", "HOÀN TIỀN");
    }

    @Test
    void latePaymentForTakenRoomNeedsRefund() {
        Booking b = book(5, 7);
        Map<String, String> params = payParams(b, true);
        bookingService.cancelByUser(guest, b.getId());
        book(5, 7); // người khác giữ phòng

        paymentService.handleVnPayResult(params);

        assertThat(reload(b).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(reload(b).getNote()).contains("HOÀN TIỀN");
    }

    @Test
    void failedOrTamperedPaymentDoesNotConfirm() {
        Booking b = book(5, 7);
        Map<String, String> failed = payParams(b, false);
        paymentService.handleVnPayResult(failed);

        Map<String, String> tampered = payParams(b, true);
        tampered.put("vnp_Amount", "100");
        assertThatThrownBy(() -> paymentService.handleVnPayResult(tampered))
                .isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.getRspCode()).isEqualTo("97"));

        Map<String, String> wrongAmount = new TreeMap<>(tampered);
        wrongAmount.remove("vnp_SecureHash");
        String query = vnPayService.signAndBuildQuery(wrongAmount);
        wrongAmount.put("vnp_SecureHash", query.substring(query.lastIndexOf('=') + 1));
        assertThatThrownBy(() -> paymentService.handleVnPayResult(wrongAmount))
                .isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.getRspCode()).isEqualTo("04"));

        assertThat(reload(b).getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void expiredBookingsAreCancelledAndLongNotesStillFit() {
        BookingForm f = form(5, 7);
        f.setNote("a".repeat(500));
        Booking b = bookingService.createBooking(guest, f);
        Booking stored = reload(b);
        stored.setCreatedAt(LocalDateTime.now().minusHours(1));
        stored.setTotalPrice(BigDecimal.valueOf(1000));
        bookingRepository.save(stored);

        bookingService.cancelExpiredBookings();

        assertThat(reload(b).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(reload(b).getNote()).startsWith("Tự hủy").hasSize(500);
    }
}
