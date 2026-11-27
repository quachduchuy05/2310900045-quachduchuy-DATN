package com.quachduchuy.exception;

/** Lỗi xử lý kết quả thanh toán, kèm mã phản hồi IPN theo quy ước của VNPay. */
public class PaymentException extends BusinessException {

    private final String rspCode;

    public PaymentException(String rspCode, String message) {
        super(message);
        this.rspCode = rspCode;
    }

    public String getRspCode() {
        return rspCode;
    }
}
