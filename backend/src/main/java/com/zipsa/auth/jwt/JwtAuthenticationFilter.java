package com.zipsa.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipsa.common.ApiResponse;
import com.zipsa.common.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer 헤더를 읽어 인증 컨텍스트를 채운다.
 * 헤더가 없으면 그냥 통과시킨다 — 비로그인 허용 엔드포인트가 많기 때문이다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Long userId = tokenProvider.parseUserId(token);
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BusinessException e) {
            // 만료/위조는 여기서 즉시 응답한다. 그래야 클라이언트가 재발급 여부를 판단할 수 있다.
            SecurityContextHolder.clearContext();
            writeError(response, e);
            return;
        }

        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }

    private void writeError(HttpServletResponse response, BusinessException e) throws IOException {
        response.setStatus(e.errorCode().status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(e.errorCode(), e.getMessage()));
    }
}
