package com.quachduchuy.service;

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
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new BusinessException("Email này đã được sử dụng");
        }

        User user = new User();
        user.setFullName(form.getFullName().trim());
        user.setEmail(form.getEmail().trim().toLowerCase());
        user.setPhone(form.getPhone());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRole(form.isRegisterAsHost() ? Role.HOST : Role.USER);
        return userRepository.save(user);
    }

    /** Lấy User đang đăng nhập từ đối tượng Principal mà Spring truyền vào controller. */
    public User getCurrentUser(Principal principal) {
        if (principal == null) {
            throw new BusinessException("Bạn cần đăng nhập");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
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
    public void changeRole(Long userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
        user.setRole(role);
    }
}
