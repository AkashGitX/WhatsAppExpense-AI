package com.akash.budgetchanger.config;

import com.akash.budgetchanger.security.JwtFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter               jwtFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF not needed — stateless API with JWT
            .csrf(csrf -> csrf.disable())

            // CORS — allow all origins (tightened via CorsConfigurationSource in AppConfig)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // Stateless session — no HttpSession, auth is token-only
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // ── Static assets ──────────────────────────────────────────
                .requestMatchers("/css/**", "/js/**", "/images/**", "/img/**", "/uploads/**", "/favicon.ico").permitAll()

                // ── Thymeleaf pages (client-side auth enforced via JWT check in app.js) ──
                .requestMatchers(HttpMethod.GET,
                        "/", "/login", "/dashboard", "/expenses/new",
                        "/chat", "/history", "/settings").permitAll()

                // ── Public REST: auth endpoints ────────────────────────────
                .requestMatchers("/auth/**").permitAll()

                // ── Twilio webhook (secured via X-Twilio-Signature) ────────
                .requestMatchers("/webhook/**").permitAll()

                // ── All other API endpoints require a valid JWT ─────────────
                .anyRequest().authenticated()
            )

            // Return 401 (not 403) for unauthenticated API requests so the
            // frontend api() helper detects a missing/expired token and redirects to login
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
            )

            // Insert JWT filter before the standard username/password filter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
