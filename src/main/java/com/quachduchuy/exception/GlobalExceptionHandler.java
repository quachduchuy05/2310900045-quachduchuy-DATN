package com.quachduchuy.exception;

import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Bắt lỗi nghiệp vụ ở mọi controller và hiển thị trang lỗi đẹp thay vì trang trắng. */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String handleTooLarge(Model model) {
        model.addAttribute("message", "Ảnh tải lên quá lớn (tối đa 5MB)");
        return "error/business";
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBusiness(BusinessException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/business";
    }
}
