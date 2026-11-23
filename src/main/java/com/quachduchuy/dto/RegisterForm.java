package com.quachduchuy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Dữ liệu từ form đăng ký. */
@Data
public class RegisterForm {

    @NotBlank(message = "Vui lòng nhập họ tên")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không hợp lệ")
    private String email;

    private String phone;

    @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự")
    private String password;

    private String confirmPassword;

    /** true nếu người dùng muốn đăng ký làm chủ nhà (Host). */
    private boolean registerAsHost;
}
