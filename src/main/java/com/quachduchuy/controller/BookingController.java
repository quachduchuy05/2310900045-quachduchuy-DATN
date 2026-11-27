package com.quachduchuy.controller;

import com.quachduchuy.service.PaymentService;
import com.quachduchuy.dto.ReviewForm;
import com.quachduchuy.service.ReviewService;
import com.quachduchuy.dto.BookingForm;
import com.quachduchuy.entity.Booking;
import com.quachduchuy.entity.User;
import com.quachduchuy.exception.BusinessException;
import com.quachduchuy.service.BookingService;
import com.quachduchuy.service.HomestayService;
import com.quachduchuy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Khách đặt phòng, xem và hủy đơn của mình. */
@Controller
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final HomestayService homestayService;
    private final UserService userService;
    private final ReviewService reviewService;
    private final PaymentService paymentService;

    /** Danh sách đơn của tôi. */
    @GetMapping
    public String myBookings(Principal principal, Model model) {
        User user = userService.getCurrentUser(principal);
        model.addAttribute("bookings", bookingService.findByUser(user));
        return "booking/my-bookings";
    }

    /** Trang xác nhận: hiện số đêm và tổng tiền trước khi tạo đơn. */
    @GetMapping("/confirm")
    public String confirm(@Valid @ModelAttribute("form") BookingForm form, BindingResult result,
                          Model model, RedirectAttributes redirect) {
        if (form.getRoomId() == null) {
            throw new BusinessException("Thiếu thông tin phòng");
        }
        String backUrl = "redirect:/homestays/"
                + homestayService.getRoom(form.getRoomId()).getHomestay().getId();
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", result.getAllErrors().get(0).getDefaultMessage());
            return backUrl;
        }
        try {
            model.addAttribute("quote", bookingService.preview(form));
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return backUrl;
        }
        model.addAttribute("holdMinutes", bookingService.getPendingExpireMinutes());
        return "booking/confirm";
    }

    /** Khách bấm "Đặt phòng" trên trang chi tiết homestay. */
    @PostMapping
    public String create(@Valid @ModelAttribute BookingForm form, BindingResult result,
                         Principal principal, RedirectAttributes redirect) {
        if (form.getRoomId() == null) {
            throw new BusinessException("Thiếu thông tin phòng");
        }
        Long homestayId = homestayService.getRoom(form.getRoomId()).getHomestay().getId();
        String backUrl = "redirect:/homestays/" + homestayId;

        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", result.getAllErrors().get(0).getDefaultMessage());
            return backUrl;
        }
        try {
            Booking booking = bookingService.createBooking(userService.getCurrentUser(principal), form);
            return "redirect:/bookings/" + booking.getId();
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return backUrl;
        }
    }

    /** Chi tiết đơn + nút thanh toán. */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model) {
        Booking booking = bookingService.getOwnedBooking(userService.getCurrentUser(principal), id);
        model.addAttribute("booking", booking);
        model.addAttribute("payments", paymentService.historyOfBooking(booking));
        model.addAttribute("deadline", bookingService.paymentDeadline(booking));
        model.addAttribute("review", reviewService.findByBooking(booking));
        model.addAttribute("canReview", reviewService.canReview(booking));
        return "booking/detail";
    }

    /** Khách đánh giá homestay sau khi đơn hoàn tất. */
    @PostMapping("/{id}/review")
    public String review(@PathVariable Long id, @Valid @ModelAttribute ReviewForm form, BindingResult result,
                         Principal principal, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", result.getAllErrors().get(0).getDefaultMessage());
        } else {
            reviewService.create(userService.getCurrentUser(principal), id, form);
            redirect.addFlashAttribute("success", "Cảm ơn bạn đã đánh giá!");
        }
        return "redirect:/bookings/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        bookingService.cancelByUser(userService.getCurrentUser(principal), id);
        redirect.addFlashAttribute("success", "Đã hủy đơn #" + id);
        return "redirect:/bookings";
    }
}
