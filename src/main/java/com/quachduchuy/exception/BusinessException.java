package com.quachduchuy.exception;

/** Lỗi nghiệp vụ có thông báo thân thiện để hiện cho người dùng (ví dụ: "Phòng đã có người đặt"). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
