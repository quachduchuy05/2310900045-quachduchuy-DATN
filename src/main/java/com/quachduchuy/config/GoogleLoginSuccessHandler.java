package com.quachduchuy.config;

import com.quachduchuy.entity.User;
import com.quachduchuy.exception.GoogleLoginException;
import com.quachduchuy.repository.UserRepository;
import com.quachduchuy.service.CustomUserDetailsService;
import com.quachduchuy.service.GoogleAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Google xác thực xong → đổi sang đúng loại phiên đăng nhập như khi dùng mật khẩu
 * (principal là email, quyền lấy từ CSDL), nhờ vậy phần còn lại của website không cần phân biệt.
 */
@Component
@RequiredArgsConstructor
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    /** Có trong session khi người đang đăng nhập bấm "Liên kết Google" ở trang hồ sơ. */
    public static final String LINK_ATTRIBUTE = "googleLinkUserId";

    private final GoogleAccountService googleAccountService;
    private final UserRepository userRepository;
    private final CustomUserDetailsService userDetailsService;

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final AuthenticationSuccessHandler redirectHandler = new SavedRequestAwareAuthenticationSuccessHandler();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OidcUser google = (OidcUser) authentication.getPrincipal();
        boolean verified = Boolean.TRUE.equals(google.getEmailVerified());

        HttpSession session = request.getSession();
        Long linkUserId = (Long) session.getAttribute(LINK_ATTRIBUTE);
        session.removeAttribute(LINK_ATTRIBUTE);

        User linking = linkUserId == null ? null : userRepository.findById(linkUserId).orElse(null);
        if (linking != null) {
            String result = "linked";
            try {
                googleAccountService.link(linking.getId(), google.getSubject(), google.getEmail(), verified);
            } catch (GoogleLoginException e) {
                result = e.getCode();
            }
            signIn(linking.getEmail(), request, response); // giữ nguyên tài khoản đang đăng nhập
            response.sendRedirect(request.getContextPath() + "/profile?google=" + result);
            return;
        }

        try {
            User user = googleAccountService.signIn(
                    google.getSubject(), google.getEmail(), verified, google.getFullName());
            Authentication local = signIn(user.getEmail(), request, response);
            redirectHandler.onAuthenticationSuccess(request, response, local);
        } catch (GoogleLoginException e) {
            SecurityContextHolder.clearContext();
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login?google=" + e.getCode());
        }
    }

    private Authentication signIn(String email, HttpServletRequest request, HttpServletResponse response) {
        UserDetails details = userDetailsService.loadUserByUsername(email);
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                details, null, details.getAuthorities());
        if (details instanceof CredentialsContainer credentials) {
            credentials.eraseCredentials();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return auth;
    }
}
