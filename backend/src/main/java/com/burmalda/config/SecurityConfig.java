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
                .csrf(csrf -> csrf.disable())          // REST + Bearer-токены: CSRF не нужен
                .cors(cors -> {})                      // настройки CORS — в WebConfig
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // для H2-консоли
                .authorizeHttpRequests(auth -> auth
                        // открыто всем
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/health").permitAll()
                        .requestMatchers("/h2-console/**", "/error").permitAll()
                        // по ролям
                        .requestMatchers("/api/accounting/**", "/api/dashboard/accountant").hasRole("ACCOUNTANT")
                        .requestMatchers("/api/dashboard/employee", "/api/wallet/**", "/api/time/**").hasRole("EMPLOYEE")
                        // остальное API — любой залогиненный
                        .requestMatchers("/api/**").authenticated()
                        // всё прочее — статика фронта (html/css/js)
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeJson(res, mapper, HttpServletResponse.SC_UNAUTHORIZED, "Нужно войти в систему"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeJson(res, mapper, HttpServletResponse.SC_FORBIDDEN, "Недостаточно прав")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * JwtAuthFilter — @Component, и Spring Boot по умолчанию повесил бы его ещё и как обычный
     * сервлет-фильтр (до Spring Security). Тогда внутри цепочки безопасности он бы уже не сработал
     * и все запросы получали бы 401. Поэтому авто-регистрацию выключаем: фильтр живёт только в цепочке выше.
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
