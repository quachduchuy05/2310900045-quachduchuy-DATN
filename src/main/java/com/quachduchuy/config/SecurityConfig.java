package com.quachduchuy.config;

import com.quachduchuy.repository.UserRepository;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
    public SecurityFilterChain filterChain(HttpSecurity http, UserRepository userRepository,
                                           GoogleLoginSettings google,
                                           GoogleLoginSuccessHandler googleSuccessHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Trang công khai: ai cũng xem được
                .requestMatchers("/", "/homestays/**", "/login", "/register",
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/error", "/error/**",
                        // VNPay gọi về / chuyển khách về, dữ liệu được xác thực bằng chữ ký
                        "/payment/vnpay-return", "/payment/vnpay-ipn").permitAll()
                // Khu vực riêng theo vai trò
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/host/**").hasAnyRole("HOST", "ADMIN")
                // Còn lại (đặt phòng, thanh toán, AI...) chỉ cần đăng nhập
                .anyRequest().authenticated()
            )
            .addFilterBefore(new AccountStatusFilter(userRepository), AuthorizationFilter.class)
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

        if (google.isConfigured()) {
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(google.clientRegistrations())
                    .loginPage("/login")
                    .successHandler(googleSuccessHandler)
                    .failureUrl("/login?google=failed"));
        }

        return http.build();
    }

    /** Mã hóa mật khẩu bằng BCrypt trước khi lưu vào DB. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
