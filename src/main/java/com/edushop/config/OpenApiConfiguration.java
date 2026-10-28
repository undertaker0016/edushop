
package com.edushop.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
public class OpenApiConfiguration {

    @Bean
    OpenAPI eduShopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EduShop API")
                        .version("1.0.0")
                        .description("REST API documentation for EduShop."))
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token issued by POST /api/v1/auth/login.")));
    }

    // Hiển thị tất cả API trong một nhóm
    @Bean
    GroupedOpenApi allApis() {
        return GroupedOpenApi.builder()
                .group("All APIs")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    GroupedOpenApi authenticationApi() {
        return GroupedOpenApi.builder()
                .group("Authentication")
                .pathsToMatch("/api/v1/auth/**")
                .build();
    }

    @Bean
    GroupedOpenApi usersApi() {
        return GroupedOpenApi.builder()
                .group("Users")
                .pathsToMatch("/api/v1/users/**")
                .build();
    }

    @Bean
    GroupedOpenApi coursesApi() {
        return GroupedOpenApi.builder()
                .group("Courses")
                .pathsToMatch(
                        "/api/v1/courses/**",
                        "/api/v1/categories/**",
                        "/api/v1/tags/**",
                        "/api/v1/instructor/courses/**")
                .build();
    }

    @Bean
    GroupedOpenApi ordersApi() {
        return GroupedOpenApi.builder()
                .group("Checkout and Orders")
                .pathsToMatch("/api/v1/orders/**")
                .build();
    }

    @Bean
    GroupedOpenApi paymentsApi() {
        return GroupedOpenApi.builder()
                .group("Payments")
                .pathsToMatch("/api/v1/payments/**")
                .build();
    }

    @Bean
    GroupedOpenApi instructorApi() {
        return GroupedOpenApi.builder()
                .group("Instructor and Offerings")
                .pathsToMatch("/api/v1/instructor/**")
                .build();
    }

    @Bean
    GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("Admin")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }

    @Bean
    GroupedOpenApi reviewsApi() {
        return GroupedOpenApi.builder()
                .group("Reviews")
                .pathsToMatch("/api/v1/reviews/**")
                .build();
    }

    @Bean
    GroupedOpenApi wishlistApi() {
        return GroupedOpenApi.builder()
                .group("Wishlists")
                .pathsToMatch("/api/v1/wishlists/**")
                .build();
    }

    @Bean
    GroupedOpenApi enrollmentApi() {
        return GroupedOpenApi.builder()
                .group("Enrollments and Classroom")
                .pathsToMatch("/api/v1/enrollments/**")
                .build();
    }

    @Bean
    GroupedOpenApi notificationApi() {
        return GroupedOpenApi.builder()
                .group("Notifications")
                .pathsToMatch("/api/v1/notifications/**")
                .build();
    }
}