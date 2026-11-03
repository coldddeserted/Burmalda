package com.burmalda.config;

import com.burmalda.security.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.Map;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtFilter,
                                                   ObjectMapper mapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())          // REST + Bearer tokens: no CSRF needed
                .cors(cors -> {})                      // CORS settings live in WebConfig
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // for the H2 console
                .authorizeHttpRequests(auth -> auth
                        // open to everyone
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/health").permitAll()
                        .requestMatchers("/h2-console/**", "/error").permitAll()
                        // by role
                        .requestMatchers("/api/accounting/**", "/api/dashboard/accountant").hasRole("ACCOUNTANT")
                        .requestMatchers("/api/dashboard/employee", "/api/wallet/**", "/api/time/**").hasRole("EMPLOYEE")
                        // the rest of the API — any logged-in user
                        .requestMatchers("/api/**").authenticated()
                        // everything else — frontend static files (html/css/js)
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeJson(res, mapper, HttpServletResponse.SC_UNAUTHORIZED, "Please log in"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeJson(res, mapper, HttpServletResponse.SC_FORBIDDEN, "Access denied")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * JwtAuthFilter is a @Component, so by default Spring Boot would also register it as a plain
     * servlet filter (before Spring Security). Then it would not run inside the security chain
     * and every request would get 401. So auto-registration is disabled: the filter lives only in the chain above.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeJson(HttpServletResponse res, ObjectMapper mapper, int status, String msg)
            throws IOException {
        res.setStatus(status);
        res.setCharacterEncoding("UTF-8");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), Map.of("message", msg));
    }
}
