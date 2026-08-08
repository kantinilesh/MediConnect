package com.mediconnect.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Typed binding for {@code mediconnect.jwt.*} properties in application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "mediconnect.jwt")
@Getter
@Setter
public class JwtConfig {

    /** JWT signing secret — minimum 256-bit (32-char) for HMAC-SHA256. */
    private String secret;

    /** Token expiry in milliseconds (default: 86 400 000 = 24 h). */
    private long expiration;
}
