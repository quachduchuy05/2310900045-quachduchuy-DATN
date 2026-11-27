package com.quachduchuy.service;

import com.quachduchuy.entity.User;
import com.quachduchuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security gọi class này khi người dùng đăng nhập:
 * tìm user theo email trong DB rồi trả về thông tin + quyền (ROLE_ADMIN / ROLE_HOST / ROLE_USER).
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(UserService.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + email));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name())   // tự thêm tiền tố ROLE_
                .disabled(!user.isEnabled())    // tài khoản bị khóa thì không đăng nhập được
                .build();
    }
}
