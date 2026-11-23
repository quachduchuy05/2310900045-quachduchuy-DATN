package com.quachduchuy.entity;

/** 3 vai trò trong hệ thống. */
public enum Role {
    ADMIN, // quản trị viên: quản lý toàn bộ người dùng, homestay, đơn đặt
    HOST,  // chủ nhà: đăng homestay, phòng và xem đơn đặt của mình
    USER   // khách: tìm phòng, đặt phòng, thanh toán, dùng AI
}
