package com.quachduchuy.entity;

/** Vòng đời của một đơn đặt phòng. */
public enum BookingStatus {
    PENDING,    // vừa đặt, đang chờ thanh toán (vẫn giữ phòng trong 15 phút)
    PAID,       // đã thanh toán VNPay thành công
    CANCELLED,  // bị hủy (khách hủy, hết hạn thanh toán, hoặc thanh toán lỗi)
    COMPLETED;  // khách đã ở xong

    /** Quản lý (Host/Admin) chỉ được chuyển trạng thái theo đúng vòng đời; đơn đã hủy/hoàn tất là kết thúc. */
    public boolean canChangeTo(BookingStatus next) {
        return switch (this) {
            case PENDING -> next == PAID || next == CANCELLED;
            case PAID -> next == COMPLETED || next == CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };
    }
}
