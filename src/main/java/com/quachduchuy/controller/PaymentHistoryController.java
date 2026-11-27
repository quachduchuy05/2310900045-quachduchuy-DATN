package com.quachduchuy.controller;

import com.quachduchuy.entity.Payment;
import com.quachduchuy.entity.PaymentStatus;
import com.quachduchuy.service.PaymentService;
import com.quachduchuy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

/** Lịch sử thanh toán theo vai trò. Quyền vào /host/** và /admin/** do SecurityConfig kiểm soát. */
@Controller
@RequiredArgsConstructor
public class PaymentHistoryController {

    private final PaymentService paymentService;
    private final UserService userService;

    @GetMapping("/payments")
    public String mine(@RequestParam(required = false) PaymentStatus status, Principal principal, Model model) {
        List<Payment> payments = paymentService.historyOfGuest(userService.getCurrentUser(principal));
        return render(model, "Lịch sử thanh toán của tôi", "/payments", true, status, payments);
    }

    @GetMapping("/host/payments")
    public String host(@RequestParam(required = false) PaymentStatus status, Principal principal, Model model) {
        List<Payment> payments = paymentService.historyOfHost(userService.getCurrentUser(principal));
        return render(model, "Giao dịch của homestay tôi", "/host/payments", false, status, payments);
    }

    @GetMapping("/admin/payments")
    public String admin(@RequestParam(required = false) PaymentStatus status, Model model) {
        return render(model, "Tất cả giao dịch", "/admin/payments", false, status, paymentService.historyAll());
    }

    private String render(Model model, String title, String path, boolean mine,
                          PaymentStatus status, List<Payment> all) {
        List<Payment> successful = all.stream().filter(p -> p.getStatus() == PaymentStatus.SUCCESS).toList();
        model.addAttribute("title", title);
        model.addAttribute("path", path);
        model.addAttribute("mine", mine);
        model.addAttribute("status", status);
        // Số liệu tổng tính trên toàn bộ, bộ lọc chỉ áp dụng cho bảng
        model.addAttribute("totalCount", all.size());
        model.addAttribute("successCount", successful.size());
        model.addAttribute("successAmount",
                successful.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("payments",
                status == null ? all : all.stream().filter(p -> p.getStatus() == status).toList());
        return "payment/history";
    }
}
