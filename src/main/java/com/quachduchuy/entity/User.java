package com.quachduchuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Bảng users: lưu tài khoản của Admin, Host và User. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password; // đã mã hóa BCrypt, không bao giờ lưu mật khẩu thô

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    private boolean enabled = true; // Admin có thể khóa tài khoản

    @Column(unique = true, length = 64)
    private String googleId; // mã định danh Google ("sub"), null nếu chưa liên kết

    private Boolean googleOnly; // true: tài khoản tạo từ Google, không dùng mật khẩu

    private LocalDateTime createdAt = LocalDateTime.now();
}
