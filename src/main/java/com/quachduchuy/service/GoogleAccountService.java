package com.quachduchuy.service;

import com.quachduchuy.entity.Role;
import com.quachduchuy.entity.User;
import com.quachduchuy.exception.GoogleLoginException;
import com.quachduchuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ghép tài khoản Google với tài khoản Staygo.
 * <p>
 * Không tự gộp vào tài khoản mật khẩu có cùng email: lúc đăng ký website không xác minh email,
 * nên người khác có thể đã tạo trước tài khoản bằng email của bạn và vẫn biết mật khẩu.
 * Chủ tài khoản phải đăng nhập bằng mật khẩu rồi tự bấm liên kết trong trang hồ sơ.
 */
@Service
@RequiredArgsConstructor
public class GoogleAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Tìm tài khoản đã liên kết, hoặc tạo tài khoản khách mới nếu email chưa từng đăng ký. */
    @Transactional
    public User signIn(String googleId, String email, boolean verified, String fullName) {
        requireVerified(email, verified);
        User user = userRepository.findByGoogleId(googleId).orElse(null);
        if (user == null) {
            String normalized = UserService.normalizeEmail(email);
            if (userRepository.existsByEmail(normalized)) {
                throw new GoogleLoginException("exists");
            }
            user = createUser(googleId, normalized, fullName);
        }
        if (!user.isEnabled()) {
            throw new GoogleLoginException("disabled");
        }
        return user;
    }

    /** Liên kết Google vào tài khoản đang đăng nhập; email hai bên phải trùng nhau. */
    @Transactional
    public void link(Long userId, String googleId, String email, boolean verified) {
        requireVerified(email, verified);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GoogleLoginException("failed"));
        boolean usedElsewhere = userRepository.findByGoogleId(googleId)
                .filter(other -> !other.getId().equals(userId))
                .isPresent();
        if (usedElsewhere || !UserService.normalizeEmail(email).equals(user.getEmail())) {
            throw new GoogleLoginException("mismatch");
        }
        user.setGoogleId(googleId);
    }

    private void requireVerified(String email, boolean verified) {
        if (email == null || !verified || email.length() > 100) {
            throw new GoogleLoginException("unverified");
        }
    }

    private User createUser(String googleId, String email, String fullName) {
        String name = (fullName == null || fullName.isBlank()) ? email : fullName.trim();
        User user = new User();
        user.setGoogleId(googleId);
        user.setGoogleOnly(true);
        user.setEmail(email);
        user.setFullName(name.substring(0, Math.min(name.length(), 100)));
        // Mật khẩu ngẫu nhiên không ai biết: tài khoản này chỉ vào được bằng Google
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole(Role.USER);
        return userRepository.save(user);
    }
}
