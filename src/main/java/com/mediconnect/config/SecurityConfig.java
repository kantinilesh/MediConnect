package com.mediconnect.config;

import com.mediconnect.security.CustomAccessDeniedHandler;
import com.mediconnect.security.CustomAuthenticationEntryPoint;
import com.mediconnect.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration.
 *
 * <ul>
 *   <li>Stateless JWT session (no HttpSession).</li>
 *   <li>CSRF disabled (safe for stateless REST APIs).</li>
 *   <li>Custom 401 / 403 handlers for clean JSON error responses.</li>
 *   <li>Public: {@code /api/v1/auth/**}, discovery reads, actuator, Swagger.</li>
 *   <li>Method-level {@code @PreAuthorize} enabled for fine-grained RBAC.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter         jwtAuthFilter;
    private final UserDetailsService              userDetailsService;
    private final CustomAuthenticationEntryPoint  authEntryPoint;
    private final CustomAccessDeniedHandler       accessDeniedHandler;

    // ── Public paths (no JWT required) ───────────────────────────────────────

    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/**",
        "/actuator/health",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    // ── Publicly readable endpoints (GET only, fine-grained role restrictions via @PreAuthorize) ─

    private static final String[] PUBLIC_GET_PATHS = {
        "/api/v1/doctors/search",
        "/api/v1/doctors/*"
    };

    // ── Beans ─────────────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)   // 401 Unauthorized
                        .accessDeniedHandler(accessDeniedHandler))   // 403 Forbidden
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
