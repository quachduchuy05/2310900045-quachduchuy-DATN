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
import org.springframework.transaction.annotation.Isolation;
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
    private static final int MAX_NOTE_LENGTH = 500;

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    @Value("${booking.pending-expire-minutes:15}")
    private int pendingExpireMinutes;

    // READ_COMMITTED: sau khi giành được khóa phải nhìn thấy đơn mà giao dịch trước vừa ghi
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(User user, BookingForm form) {
        // ---- Bước 1: kiểm tra dữ liệu ngày ----
        long nights = checkDates(form);

        // ---- Bước 2: lấy phòng và KHÓA lại cho tới hết transaction ----
        Room room = lockRoom(form.getRoomId());

        // ---- Bước 3: kiểm tra phòng, sức chứa và trùng lịch ----
        checkRoom(room, form);

        // ---- Bước 4: tạo đơn ở trạng thái chờ thanh toán ----
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoom(room);
        booking.setCheckIn(form.getCheckIn());
        booking.setCheckOut(form.getCheckOut());
        booking.setGuests(form.getGuests());
        booking.setNote(form.getNote());
        booking.setTotalPrice(totalPrice(room, nights));
        booking.setStatus(BookingStatus.PENDING);
        return bookingRepository.save(booking);
    }

    /** Thông tin tạm tính hiển thị ở trang xác nhận. */
    public record Quote(Room room, long nights, BigDecimal total) {
    }

    /**
     * Kiểm tra và tính tiền mà chưa tạo đơn. Phòng chưa được giữ ở bước này,
     * nên createBooking vẫn kiểm tra lại toàn bộ khi khách xác nhận.
     */
    @Transactional(readOnly = true)
    public Quote preview(BookingForm form) {
        long nights = checkDates(form);
        Room room = roomRepository.findById(form.getRoomId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng"));
        checkRoom(room, form);
        room.getHomestay().getName(); // nạp sẵn để trang xác nhận dùng được
        return new Quote(room, nights, totalPrice(room, nights));
    }

    public int getPendingExpireMinutes() {
        return pendingExpireMinutes;
    }

    private long checkDates(BookingForm form) {
        LocalDate checkIn = form.getCheckIn();
        LocalDate checkOut = form.getCheckOut();
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
        return nights;
    }

    private void checkRoom(Room room, BookingForm form) {
        if (!room.isAvailable() || !room.getHomestay().isActive()) {
            throw new BusinessException("Phòng này hiện không nhận khách");
        }
        if (form.getGuests() > room.getCapacity()) {
            throw new BusinessException("Phòng chỉ chứa tối đa " + room.getCapacity() + " khách");
        }
        if (!isRoomFree(room.getId(), form.getCheckIn(), form.getCheckOut())) {
            throw new BusinessException("Rất tiếc, phòng đã có người đặt trong khoảng ngày này. Vui lòng chọn ngày khác.");
        }
    }

    private BigDecimal totalPrice(Room room, long nights) {
        return room.getPricePerNight().multiply(BigDecimal.valueOf(nights));
    }

    /** true nếu phòng còn trống trong khoảng [checkIn, checkOut). */
    public boolean isRoomFree(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        return bookingRepository.countOverlapping(roomId, checkIn, checkOut, ACTIVE_STATUSES) == 0;
    }

    /** Khóa dòng phòng tới hết transaction hiện tại. */
    public Room lockRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng"));
    }

    /** Khóa dòng đơn đặt tới hết transaction hiện tại. */
    public Booking lockBooking(Long id) {
        return bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn đặt phòng"));
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelByUser(User user, Long bookingId) {
        lockBooking(bookingId);
        Booking b = getOwnedBooking(user, bookingId);
        if (b.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("Chỉ hủy được đơn đang chờ thanh toán. Đơn đã thanh toán vui lòng liên hệ chủ nhà.");
        }
        b.setStatus(BookingStatus.CANCELLED);
    }

    /** Host (chủ phòng) hoặc Admin đổi trạng thái đơn, ví dụ đánh dấu khách đã ở xong. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateStatusByManager(User manager, Long bookingId, BookingStatus newStatus) {
        Booking b = lockBooking(bookingId);
        boolean isOwnerHost = b.getRoom().getHomestay().getHost().getId().equals(manager.getId());
        if (!isOwnerHost && manager.getRole() != Role.ADMIN) {
            throw new BusinessException("Bạn không có quyền với đơn này");
        }
        if (!b.getStatus().canChangeTo(newStatus)) {
            throw new BusinessException("Không thể chuyển đơn từ " + b.getStatus() + " sang " + newStatus);
        }
        // Chỉ Admin được xác nhận đã thu tiền ngoài cổng VNPay
        if (newStatus == BookingStatus.PAID && manager.getRole() != Role.ADMIN) {
            throw new BusinessException("Chỉ Admin được đánh dấu đơn đã thanh toán");
        }
        if (newStatus == BookingStatus.COMPLETED && LocalDate.now().isBefore(b.getCheckIn())) {
            throw new BusinessException("Khách chưa tới ngày nhận phòng nên chưa thể hoàn tất đơn");
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
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

    /** Chạy mỗi giờ: đơn đã thanh toán và qua ngày trả phòng được chuyển sang hoàn tất. */
    @Scheduled(fixedRate = 3_600_000)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void completeFinishedBookings() {
        bookingRepository.findByStatusAndCheckOutBefore(BookingStatus.PAID, LocalDate.now())
                .forEach(b -> b.setStatus(BookingStatus.COMPLETED));
    }

    // ---- Thống kê cho Admin ----
    public long countByStatus(BookingStatus status) {
        return bookingRepository.countByStatus(status);
    }

    public BigDecimal totalRevenue() {
        return bookingRepository.sumRevenue(Set.of(BookingStatus.PAID, BookingStatus.COMPLETED));
    }

    /** Nối ghi chú; nếu quá dài thì đưa phần mới lên đầu rồi cắt cho vừa cột. */
    public String appendNote(String oldNote, String extra) {
        if (oldNote == null || oldNote.isBlank()) {
            return extra;
        }
        String joined = oldNote + " | " + extra;
        if (joined.length() <= MAX_NOTE_LENGTH) {
            return joined;
        }
        String reordered = extra + " | " + oldNote;
        return reordered.substring(0, Math.min(reordered.length(), MAX_NOTE_LENGTH));
    }
}
