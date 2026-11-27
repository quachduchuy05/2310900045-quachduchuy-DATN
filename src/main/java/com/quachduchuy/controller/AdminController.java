package com.quachduchuy.controller;

import com.quachduchuy.service.ReviewService;
import com.quachduchuy.entity.BookingStatus;
import com.quachduchuy.entity.Role;
import com.quachduchuy.service.BookingService;
import com.quachduchuy.service.HomestayService;
import com.quachduchuy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Khu vực QUẢN TRỊ (/admin/**): thống kê, quản lý người dùng, homestay, đơn đặt. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final HomestayService homestayService;
    private final BookingService bookingService;
    private final ReviewService reviewService;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userService.findAll().size());
        model.addAttribute("totalHomestays", homestayService.findAll().size());
        model.addAttribute("pendingCount", bookingService.countByStatus(BookingStatus.PENDING));
        model.addAttribute("paidCount", bookingService.countByStatus(BookingStatus.PAID));
        model.addAttribute("revenue", bookingService.totalRevenue());
        model.addAttribute("users", userService.findAll());
        model.addAttribute("homestays", homestayService.findAll());
        model.addAttribute("bookings", bookingService.findAll());
        model.addAttribute("roles", Role.values());
        model.addAttribute("statuses", BookingStatus.values());
        return "admin/dashboard";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id, RedirectAttributes redirect) {
        userService.toggleEnabled(id);
        redirect.addFlashAttribute("success", "Đã đổi trạng thái tài khoản #" + id);
        return "redirect:/admin#users";
    }

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam Role role,
                             Principal principal, RedirectAttributes redirect) {
        userService.changeRole(userService.getCurrentUser(principal), id, role);
        redirect.addFlashAttribute("success", "Đã đổi vai trò tài khoản #" + id);
        return "redirect:/admin#users";
    }

    @PostMapping("/homestays/{id}/toggle")
    public String toggleHomestay(@PathVariable Long id, RedirectAttributes redirect) {
        homestayService.toggleActive(id);
        redirect.addFlashAttribute("success", "Đã đổi trạng thái homestay #" + id);
        return "redirect:/admin#homestays";
    }

    @PostMapping("/reviews/{id}/delete")
    public String deleteReview(@PathVariable Long id, RedirectAttributes redirect) {
        Long homestayId = reviewService.delete(id);
        redirect.addFlashAttribute("success", "Đã xóa đánh giá");
        return "redirect:/homestays/" + homestayId;
    }

    @PostMapping("/bookings/{id}/status")
    public String bookingStatus(@PathVariable Long id, @RequestParam BookingStatus status,
                                Principal principal, RedirectAttributes redirect) {
        bookingService.updateStatusByManager(userService.getCurrentUser(principal), id, status);
        redirect.addFlashAttribute("success", "Đã cập nhật đơn #" + id);
        return "redirect:/admin#bookings";
    }
}
