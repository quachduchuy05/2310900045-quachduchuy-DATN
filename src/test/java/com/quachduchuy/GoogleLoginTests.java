package com.quachduchuy;

import com.quachduchuy.config.GoogleLoginSuccessHandler;
import com.quachduchuy.entity.Role;
import com.quachduchuy.entity.User;
import com.quachduchuy.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Kiểm thử đăng nhập Google. Không gọi Google thật: giả lập kết quả Google trả về. */
@SpringBootTest(properties = {"app.google.client-id=test-id", "app.google.client-secret=test-secret"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class GoogleLoginTests {

    @Autowired MockMvc mvc;
    @Autowired GoogleLoginSuccessHandler successHandler;
    @Autowired UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        userRepository.findByEmail("khach.moi@gmail.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("user@staygo.vn").ifPresent(u -> {
            u.setGoogleId(null);
            userRepository.save(u);
        });
    }

    /** Chạy bước xử lý sau khi Google xác thực, trả về địa chỉ được chuyển tới. */
    private String googleReturns(MockHttpSession session, String subject, String email, boolean verified)
            throws Exception {
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", subject, "email", email, "email_verified", verified, "name", "Khách Mới"));
        DefaultOidcUser principal = new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), token);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(request, response,
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
        return response.getRedirectedUrl();
    }

    @Test
    void loginPageOffersGoogleAndRedirectsThere() throws Exception {
        mvc.perform(get("/login")).andExpect(content().string(containsString("/oauth2/authorization/google")));
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://accounts.google.com/**"));
        // đăng nhập bằng mật khẩu và chặn trang riêng vẫn như cũ
        mvc.perform(get("/bookings")).andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    void newGoogleUserGetsGuestAccountAndWorkingSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        assertThat(googleReturns(session, "g-1", "Khach.Moi@gmail.com", true)).isEqualTo("/");

        User created = userRepository.findByEmail("khach.moi@gmail.com").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.USER);
        assertThat(created.getGoogleId()).isEqualTo("g-1");
        mvc.perform(get("/bookings").session(session)).andExpect(status().isOk());
        mvc.perform(get("/profile").session(session))
                .andExpect(content().string(containsString("đăng nhập bằng Google")));
        mvc.perform(get("/admin").session(session)).andExpect(status().isForbidden());

        // lần sau dùng lại đúng tài khoản đó
        long count = userRepository.count();
        googleReturns(new MockHttpSession(), "g-1", "khach.moi@gmail.com", true);
        assertThat(userRepository.count()).isEqualTo(count);
    }

    @Test
    void refusesUnverifiedLockedAndUnlinkedPasswordAccounts() throws Exception {
        assertThat(googleReturns(new MockHttpSession(), "g-2", "khach.moi@gmail.com", false))
                .isEqualTo("/login?google=unverified");

        MockHttpSession session = new MockHttpSession();
        assertThat(googleReturns(session, "g-3", "user@staygo.vn", true)).isEqualTo("/login?google=exists");
        assertThat(session.isInvalid()).isTrue();

        googleReturns(new MockHttpSession(), "g-4", "khach.moi@gmail.com", true);
        User created = userRepository.findByEmail("khach.moi@gmail.com").orElseThrow();
        created.setEnabled(false);
        userRepository.save(created);
        assertThat(googleReturns(new MockHttpSession(), "g-4", "khach.moi@gmail.com", true))
                .isEqualTo("/login?google=disabled");
    }

    @Test
    void ownerLinksGoogleFromProfile() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc
                .perform(post("/profile/google").with(user("user@staygo.vn").roles("USER")).with(csrf()))
                .andExpect(header().string("Location", startsWith("/oauth2/authorization/google")))
                .andReturn().getRequest().getSession(false);

        assertThat(googleReturns(session, "g-5", "nguoi.khac@gmail.com", true))
                .isEqualTo("/profile?google=mismatch");
        assertThat(userRepository.findByEmail("user@staygo.vn").orElseThrow().getGoogleId()).isNull();

        session.setAttribute(GoogleLoginSuccessHandler.LINK_ATTRIBUTE,
                userRepository.findByEmail("user@staygo.vn").orElseThrow().getId());
        assertThat(googleReturns(session, "g-5", "user@staygo.vn", true)).isEqualTo("/profile?google=linked");
        assertThat(googleReturns(new MockHttpSession(), "g-5", "user@staygo.vn", true)).isEqualTo("/");
    }
}
