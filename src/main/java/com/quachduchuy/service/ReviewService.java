package com.quachduchuy.service;

import com.quachduchuy.dto.ReviewForm;
import com.quachduchuy.entity.Booking;
import com.quachduchuy.entity.BookingStatus;
import com.quachduchuy.entity.Review;
import com.quachduchuy.entity.User;
import com.quachduchuy.exception.BusinessException;
import com.quachduchuy.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ đánh giá homestay. */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingService bookingService;

    /** Đánh giá đã viết cho đơn này, hoặc null nếu chưa có. */
    public Review findByBooking(Booking booking) {
        return reviewRepository.findByBooking(booking).orElse(null);
    }

    /** Chỉ đơn đã hoàn tất và chưa đánh giá mới được viết đánh giá. */
    public boolean canReview(Booking booking) {
        return booking.getStatus() == BookingStatus.COMPLETED && !reviewRepository.existsByBooking(booking);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Review create(User user, Long bookingId, ReviewForm form) {
        bookingService.lockBooking(bookingId); // chặn gửi hai lần cùng lúc
        Booking booking = bookingService.getOwnedBooking(user, bookingId);
        if (!canReview(booking)) {
            throw new BusinessException("Chỉ đánh giá được một lần cho đơn đã hoàn tất");
        }
        Review review = new Review();
        review.setBooking(booking);
        review.setUser(user);
        review.setHomestay(booking.getRoom().getHomestay());
        review.setRating(form.getRating());
        review.setComment(form.getComment() == null ? null : form.getComment().trim());
        return reviewRepository.save(review);
    }

    /** Admin xóa đánh giá vi phạm. Trả về mã homestay để quay lại trang chi tiết. */
    @Transactional
    public Long delete(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đánh giá"));
        Long homestayId = review.getHomestay().getId();
        review.getHomestay().getReviews().remove(review);
        reviewRepository.delete(review);
        return homestayId;
    }
}
