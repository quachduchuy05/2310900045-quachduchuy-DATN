package com.quachduchuy.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;

/** Thêm vài biến dùng chung cho mọi trang Thymeleaf. */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("today")
    public LocalDate today() {
        return LocalDate.now();
    }
}
