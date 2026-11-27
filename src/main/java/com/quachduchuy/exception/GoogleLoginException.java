package com.quachduchuy.exception;

/** Không đăng nhập/liên kết được bằng Google; code dùng để chọn thông báo trên giao diện. */
public class GoogleLoginException extends BusinessException {

    private final String code;

    public GoogleLoginException(String code) {
        super("Đăng nhập Google không thành công: " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
