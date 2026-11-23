package com.quachduchuy.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Bắt lỗi nghiệp vụ ở mọi controller và hiển thị trang lỗi đẹp thay vì trang trắng. */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/business";
    }
}
