package com.quachduchuy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Component;

/** Khóa OAuth2 của Google. Để trống thì chức năng đăng nhập Google tắt, website vẫn chạy bình thường. */
@Component
public class GoogleLoginSettings {

    private final String clientId;
    private final String clientSecret;

    public GoogleLoginSettings(@Value("${app.google.client-id:}") String clientId,
                               @Value("${app.google.client-secret:}") String clientSecret) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
    }

    public boolean isConfigured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    ClientRegistrationRepository clientRegistrations() {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());
    }
}
