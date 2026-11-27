package com.quachduchuy.service;

import com.quachduchuy.dto.PasswordForm;
import com.quachduchuy.dto.ProfileForm;
import com.quachduchuy.dto.RegisterForm;
import com.quachduchuy.entity.Role;
import com.quachduchuy.entity.User;
import com.quachduchuy.exception.BusinessException;
import com.quachduchuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Đăng ký tài khoản mới (USER hoặc HOST). */
    @Transactional
    public User register(RegisterForm form) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("Mật khẩu nhập lại không khớp");
        }
        String email = normalizeEmail(form.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email này đã được sử dụng");
        }

        User user = new User();
        user.setFullName(form.getFullName().trim());
        user.setEmail(email);
        user.setPhone(form.getPhone());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRole(form.isRegisterAsHost() ? Role.HOST : Role.USER);
        return userRepository.save(user);
    }

    /** Email được lưu và so sánh ở dạng chữ thường, không khoảng trắng thừa. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Lấy User đang đăng nhập từ đối tượng Principal mà Spring truyền vào controller. */
    public User getCurrentUser(Principal principal) {
        if (principal == null) {
            throw new BusinessException("Bạn cần đăng nhập");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
    }

    @Transactional
    public void updateProfile(User currentUser, ProfileForm form) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
        user.setFullName(form.getFullName().trim());
        user.setPhone(form.getPhone());
    }

    @Transactional
    public void changePassword(User currentUser, PasswordForm form) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
        if (Boolean.TRUE.equals(user.getGoogleOnly())) {
            throw new BusinessException("Tài khoản đăng nhập bằng Google không dùng mật khẩu");
        }
        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Mật khẩu hiện tại không đúng");
        }
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("Mật khẩu nhập lại không khớp");
        }
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    /** Admin khóa / mở khóa tài khoản. */
    @Transactional
    public void toggleEnabled(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException("Không thể khóa tài khoản Admin");
        }
        user.setEnabled(!user.isEnabled());
    }

    /** Admin đổi vai trò của tài khoản. */
    @Transactional
    public void changeRole(User admin, Long userId, Role role) {
        if (admin.getId().equals(userId)) {
            throw new BusinessException("Bạn không thể tự đổi vai trò của chính mình");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
        user.setRole(role);
    }
}
