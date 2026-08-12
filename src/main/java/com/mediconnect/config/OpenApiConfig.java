package com.mediconnect.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI configuration.
 *
 * <p>Defines the global {@code bearerAuth} JWT security scheme so that
 * protected endpoints can show the lock icon in Swagger UI and the
 * {@code Authorization: Bearer <token>} header is auto-sent when a token
 * is entered.</p>
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title       = "MediConnect API",
        version     = "v1",
        description = "Doctor-discovery and appointment-booking microservice",
        contact     = @Contact(name = "MediConnect Team")
    ),
    security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name   = "bearerAuth",
    type   = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description  = "Enter your JWT access token. Obtain it from POST /api/v1/auth/login."
)
public class OpenApiConfig {
    // All configuration is annotation-driven.
}
