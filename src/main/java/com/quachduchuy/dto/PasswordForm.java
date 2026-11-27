package com.quachduchuy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Form đổi mật khẩu. */
@Data
public class PasswordForm {

    @NotBlank(message = "Nhập mật khẩu hiện tại")
    private String currentPassword;

    @NotBlank(message = "Nhập mật khẩu mới")
    @Size(min = 6, max = 24, message = "Mật khẩu từ 6 đến 24 ký tự")
    private String newPassword;

    private String confirmPassword;
}
