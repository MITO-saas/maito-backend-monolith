package com.maito.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.auth.filter.JwtAuthenticationFilter;
import com.maito.auth.jwt.JwtTokenProvider;
import com.maito.shared.api.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtTokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(tokenProvider, objectMapper);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(customAuthenticationEntryPoint())
                .accessDeniedHandler(customAccessDeniedHandler())
            )
            .authorizeHttpRequests(auth -> auth
                // Public Ingress
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/admin.html",
                    "/assets/**",
                    "/favicon.ico",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**",
                    "/actuator/health/**",
                    "/actuator/**",
                    "/api/v1/health/**",
                    "/api/v1/health",
                    "/api/v1/help/**",
                    "/api/v1/help",
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh",
                    "/api/v1/internal/platform/**",
                    "/error"
                ).permitAll()
                // Public Storefront Layouts
                .requestMatchers(HttpMethod.GET, "/api/v1/cms/pages/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/store/settings").permitAll()
                // Public Storefront Cart & Promotions & Payment Webhook
                .requestMatchers(HttpMethod.GET, "/api/v1/cart").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/cart/items").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/v1/cart/items/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/cart/items/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/promotions/apply").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/checkout/payment-callback").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/checkout/payment-callback/**").permitAll()

                // Public Payment Gateway Ingress & Webhook
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/initialize").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/verify-signature").permitAll()

                // Public Storefront Fulfillment Tracking
                .requestMatchers(HttpMethod.GET, "/api/v1/fulfillment/track/**").permitAll()

                // Customer Wallet Ingress
                .requestMatchers("/api/v1/wallet/**").hasAnyRole("TENANT_CUSTOMER", "CUSTOMER", "TENANT_ADMIN", "ADMIN")

                // Customer Returns & Support Helpdesk Ingress
                .requestMatchers("/api/v1/returns/**").hasAnyRole("TENANT_CUSTOMER", "CUSTOMER", "TENANT_ADMIN", "ADMIN")
                .requestMatchers("/api/v1/support/**").hasAnyRole("TENANT_CUSTOMER", "CUSTOMER", "TENANT_ADMIN", "ADMIN")

                // Customer B2B Wholesale Portal Ingress
                .requestMatchers("/api/v1/b2b/**").hasAnyRole("TENANT_CUSTOMER", "CUSTOMER", "TENANT_ADMIN", "ADMIN")

                // Customer Profile Gated Ingress
                .requestMatchers(HttpMethod.POST, "/api/v1/cart/merge").hasAnyRole("TENANT_CUSTOMER", "CUSTOMER", "TENANT_ADMIN", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/checkout/create-order").hasAnyRole("TENANT_CUSTOMER", "CUSTOMER", "TENANT_ADMIN", "ADMIN")

                // Tenant Admin Ingress (including Admin Wallet adjustment)
                .requestMatchers("/api/v1/admin/**").hasAnyRole("TENANT_ADMIN", "ADMIN")

                // Customer Account Ingress
                .requestMatchers("/api/v1/account/**").hasAnyRole("TENANT_CUSTOMER", "CUSTOMER", "TENANT_ADMIN", "ADMIN")

                // Auth Me
                .requestMatchers("/api/v1/auth/me").authenticated()

                // Any other request
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> apiResponse = ApiResponse.fail("UNAUTHORIZED", "Authentication required: " + authException.getMessage());
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        };
    }

    @Bean
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> apiResponse = ApiResponse.fail("ACCESS_DENIED", "Access denied: insufficient privileges");
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        };
    }
}
