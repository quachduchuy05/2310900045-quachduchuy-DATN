package com.quachduchuy.entity;

/** Vòng đời của một đơn đặt phòng. */
public enum BookingStatus {
    PENDING,    // vừa đặt, đang chờ thanh toán (vẫn giữ phòng trong 15 phút)
    PAID,       // đã thanh toán VNPay thành công
    CANCELLED,  // bị hủy (khách hủy, hết hạn thanh toán, hoặc thanh toán lỗi)
    COMPLETED   // khách đã ở xong
}
