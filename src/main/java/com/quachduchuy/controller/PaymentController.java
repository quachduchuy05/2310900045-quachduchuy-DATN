package com.quachduchuy.controller;

import com.quachduchuy.entity.Payment;
import com.quachduchuy.exception.BusinessException;
import com.quachduchuy.exception.PaymentException;
import com.quachduchuy.service.PaymentService;
import com.quachduchuy.service.UserService;
import com.quachduchuy.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/** Thanh toán VNPay. */
@Slf4j
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final VnPayService vnPayService;
    private final UserService userService;

    /** Bước 1: khách bấm "Thanh toán VNPay" → chuyển sang trang VNPay. */
    @PostMapping("/create/{bookingId}")
    public String create(@PathVariable Long bookingId, Principal principal, HttpServletRequest request) {
        String url = paymentService.createPaymentUrl(
                userService.getCurrentUser(principal), bookingId, getClientIp(request));
        return "redirect:" + url;
    }

    /** Bước 2: VNPay chuyển khách quay về đây kèm kết quả. */
    @GetMapping("/vnpay-return")
    public String vnpayReturn(@RequestParam Map<String, String> params, Model model) {
        Payment payment = paymentService.handleVnPayResult(params).payment();
        model.addAttribute("payment", payment);
        model.addAttribute("booking", payment.getBooking());
        return "payment/result";
    }

    /**
     * IPN: VNPay gọi ngầm server-to-server để báo kết quả (chắc chắn hơn Return URL,
     * vì khách có thể tắt trình duyệt trước khi được chuyển về).
     * Khi deploy thật, khai báo URL này trên trang quản trị VNPay.
     */
    @GetMapping("/vnpay-ipn")
    @ResponseBody
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        try {
            if (paymentService.handleVnPayResult(params).alreadyProcessed()) {
                return Map.of("RspCode", "02", "Message", "Order already confirmed");
            }
            return Map.of("RspCode", "00", "Message", "Confirm Success");
        } catch (PaymentException e) {
            return Map.of("RspCode", e.getRspCode(), "Message", e.getMessage());
        } catch (Exception e) {
            log.error("Xử lý IPN thất bại", e);
            return Map.of("RspCode", "99", "Message", "Unknown error");
        }
    }

    // ===================== CỔNG GIẢ LẬP (chỉ khi chưa có key VNPay) =====================

    @GetMapping("/demo-gateway")
    public String demoGateway(@RequestParam String txnRef, Principal principal, Model model) {
        ensureDemoMode();
        model.addAttribute("payment",
                paymentService.getOwnedPayment(userService.getCurrentUser(principal), txnRef));
        return "payment/demo-gateway";
    }

    @PostMapping("/demo-gateway")
    public String demoGatewaySubmit(@RequestParam String txnRef, @RequestParam boolean success,
                                    Principal principal) {
        ensureDemoMode();
        return "redirect:/payment/vnpay-return?" + paymentService.buildDemoReturnQuery(
                userService.getCurrentUser(principal), txnRef, success);
    }

    private void ensureDemoMode() {
        if (vnPayService.isConfigured()) {
            throw new BusinessException("Cổng giả lập đã tắt vì VNPay thật đã được cấu hình");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank()) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
