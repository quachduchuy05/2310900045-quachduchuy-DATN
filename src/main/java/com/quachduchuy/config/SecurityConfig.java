package com.quachduchuy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình PHÂN QUYỀN: trang nào ai được vào.
 * Người dùng được nạp từ CSDL bởi CustomUserDetailsService (package service).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Trang công khai: ai cũng xem được
                .requestMatchers("/", "/homestays/**", "/login", "/register",
                        "/css/**", "/js/**", "/images/**", "/error/**",
                        "/payment/vnpay-ipn").permitAll()
                // Khu vực riêng theo vai trò
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/host/**").hasAnyRole("HOST", "ADMIN")
                // Còn lại (đặt phòng, thanh toán, AI...) chỉ cần đăng nhập
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout")
                .permitAll()
            )
            .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"));

        return http.build();
    }

    /** Mã hóa mật khẩu bằng BCrypt trước khi lưu vào DB. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
