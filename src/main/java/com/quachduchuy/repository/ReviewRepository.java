package com.quachduchuy.repository;

import com.quachduchuy.entity.Booking;
import com.quachduchuy.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByBooking(Booking booking);

    boolean existsByBooking(Booking booking);
}
