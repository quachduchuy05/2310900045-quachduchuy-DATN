package com.quachduchuy.service;

import com.quachduchuy.dto.BookingForm;
import com.quachduchuy.entity.*;
import com.quachduchuy.exception.BusinessException;
import com.quachduchuy.repository.BookingRepository;
import com.quachduchuy.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Nghiệp vụ ĐẶT PHÒNG, trọng tâm là CHỐNG TRÙNG LỊCH.
 *
 * Cách chống trùng gồm 2 lớp:
 *  1. Khóa dòng phòng trong DB (SELECT ... FOR UPDATE) để các yêu cầu đặt cùng một phòng
 *     phải chạy lần lượt, không chạy song song.
 *  2. Trong lúc giữ khóa, đếm số đơn còn hiệu lực có khoảng ngày giao nhau. Có ≥ 1 đơn thì từ chối.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    /** Các trạng thái được coi là "đang giữ phòng". */
    public static final Set<BookingStatus> ACTIVE_STATUSES = Set.of(BookingStatus.PENDING, BookingStatus.PAID);

    private static final int MAX_NIGHTS = 30;

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    @Value("${booking.pending-expire-minutes:15}")
    private int pendingExpireMinutes;

    @Transactional
    public Booking createBooking(User user, BookingForm form) {
        LocalDate checkIn = form.getCheckIn();
        LocalDate checkOut = form.getCheckOut();

        // ---- Bước 1: kiểm tra dữ liệu ngày ----
        if (checkIn.isBefore(LocalDate.now())) {
            throw new BusinessException("Ngày nhận phòng không được ở quá khứ");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BusinessException("Ngày trả phòng phải sau ngày nhận phòng");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > MAX_NIGHTS) {
            throw new BusinessException("Chỉ được đặt tối đa " + MAX_NIGHTS + " đêm");
        }

        // ---- Bước 2: lấy phòng và KHÓA lại cho tới hết transaction ----
        Room room = roomRepository.findByIdForUpdate(form.getRoomId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng"));

        if (!room.isAvailable() || !room.getHomestay().isActive()) {
            throw new BusinessException("Phòng này hiện không nhận khách");
        }
        if (form.getGuests() > room.getCapacity()) {
            throw new BusinessException("Phòng chỉ chứa tối đa " + room.getCapacity() + " khách");
        }

        // ---- Bước 3: kiểm tra trùng lịch ----
        if (!isRoomFree(room.getId(), checkIn, checkOut)) {
            throw new BusinessException("Rất tiếc, phòng đã có người đặt trong khoảng ngày này. Vui lòng chọn ngày khác.");
        }

        // ---- Bước 4: tạo đơn ở trạng thái chờ thanh toán ----
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoom(room);
        booking.setCheckIn(checkIn);
        booking.setCheckOut(checkOut);
        booking.setGuests(form.getGuests());
        booking.setNote(form.getNote());
        booking.setTotalPrice(room.getPricePerNight().multiply(BigDecimal.valueOf(nights)));
        booking.setStatus(BookingStatus.PENDING);
        return bookingRepository.save(booking);
    }

    /** true nếu phòng còn trống trong khoảng [checkIn, checkOut). */
    public boolean isRoomFree(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        return bookingRepository.countOverlapping(roomId, checkIn, checkOut, ACTIVE_STATUSES) == 0;
    }

    /** Các khoảng ngày đã bị đặt của phòng, để hiển thị cho khách tránh chọn. */
    public List<Booking> getBookedRanges(Long roomId) {
        return bookingRepository.findActiveByRoom(roomId, LocalDate.now(), ACTIVE_STATUSES);
    }

    public Booking getBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn đặt phòng"));
    }

    /** Lấy đơn và đảm bảo đơn đó thuộc về người đang đăng nhập. */
    public Booking getOwnedBooking(User user, Long id) {
        Booking b = getBooking(id);
        if (!b.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền xem đơn này");
        }
        return b;
    }

    public List<Booking> findByUser(User user) {
        return bookingRepository.findByUserWithRoom(user);
    }

    public List<Booking> findByHost(User host) {
        return bookingRepository.findByHost(host);
    }

    public List<Booking> findAll() {
        return bookingRepository.findAllWithDetails();
    }

    /** Khách tự hủy đơn chưa thanh toán. */
    @Transactional
    public void cancelByUser(User user, Long bookingId) {
        Booking b = getOwnedBooking(user, bookingId);
        if (b.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("Chỉ hủy được đơn đang chờ thanh toán. Đơn đã thanh toán vui lòng liên hệ chủ nhà.");
        }
        b.setStatus(BookingStatus.CANCELLED);
    }

    /** Host (chủ phòng) hoặc Admin đổi trạng thái đơn, ví dụ đánh dấu khách đã ở xong. */
    @Transactional
    public void updateStatusByManager(User manager, Long bookingId, BookingStatus newStatus) {
        Booking b = getBooking(bookingId);
        boolean isOwnerHost = b.getRoom().getHomestay().getHost().getId().equals(manager.getId());
        if (!isOwnerHost && manager.getRole() != Role.ADMIN) {
            throw new BusinessException("Bạn không có quyền với đơn này");
        }
        b.setStatus(newStatus);
    }

    /** Hạn chót thanh toán của một đơn PENDING. */
    public LocalDateTime paymentDeadline(Booking b) {
        return b.getCreatedAt().plusMinutes(pendingExpireMinutes);
    }

    /**
     * Chạy tự động mỗi phút: hủy các đơn PENDING quá hạn thanh toán để nhả phòng cho người khác.
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void cancelExpiredBookings() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(pendingExpireMinutes);
        List<Booking> expired = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, deadline);
        for (Booking b : expired) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setNote(appendNote(b.getNote(), "Tự hủy do quá hạn thanh toán"));
        }
        if (!expired.isEmpty()) {
            log.info("Đã tự hủy {} đơn quá hạn thanh toán", expired.size());
        }
    }

    // ---- Thống kê cho Admin ----
    public long countByStatus(BookingStatus status) {
        return bookingRepository.countByStatus(status);
    }

    public BigDecimal totalRevenue() {
        return bookingRepository.sumRevenue(Set.of(BookingStatus.PAID, BookingStatus.COMPLETED));
    }

    private String appendNote(String oldNote, String extra) {
        return (oldNote == null || oldNote.isBlank()) ? extra : oldNote + " | " + extra;
    }
}
