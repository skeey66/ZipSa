package com.zipsa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipsa.auth.jwt.JwtAuthenticationFilter;
import com.zipsa.common.ApiResponse;
import com.zipsa.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    /** 비로그인 허용 경로. docs/api 의 「인증: 없음」 오퍼레이션과 일치시킨다. */
    private static final String[] PUBLIC_POST = {
            "/api/auth/signup", "/api/auth/login", "/api/auth/reissue"
    };
    /** 와일드카드 공개 경로 안에 있지만 로그인이 필요한 GET. 순서상 먼저 검사한다. */
    private static final String[] AUTHENTICATED_GET = {
            "/api/policies/recommend",
            "/api/policies/bookmarks"
    };
    private static final String[] PUBLIC_GET = {
            "/api/auth/check-id",
            "/api/policies/**",
            "/api/public-housings/**",
            "/api/transactions/**",
            "/api/regions/**",
            "/api/posts/**",
            "/api/news/**",
            // AI 연동 대비 목업. 정적 응답만 돌려주고 DB 도 회원 정보도 쓰지 않으므로 공개한다.
            "/api/mock/**",
            "/swagger-ui/**", "/v3/api-docs/**",
            "/actuator/health"
    };

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    @Value("${zipsa.cors.allowed-origins}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // JWT 를 헤더로 받는 stateless API 라 CSRF 토큰이 필요 없다.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 로그인 전용 GET 은 PUBLIC_GET 의 와일드카드보다 먼저 걸어야 한다.
                        // 뒤에 두면 /api/policies/** 가 먼저 매칭돼 비로그인도 통과한다.
                        .requestMatchers(HttpMethod.GET, AUTHENTICATED_GET).authenticated()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, ErrorCode.INVALID_TOKEN))
                        .accessDeniedHandler((req, res, ex) -> write(res, ErrorCode.ACCESS_DENIED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** 인증 실패도 공통 응답 봉투로 내려야 프론트가 한 갈래로 처리할 수 있다. */
    private void write(jakarta.servlet.http.HttpServletResponse response, ErrorCode code) throws java.io.IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, code.defaultMessage()));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
