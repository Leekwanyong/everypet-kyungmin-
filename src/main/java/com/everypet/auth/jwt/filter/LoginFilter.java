package com.everypet.auth.jwt.filter;

import com.everypet.auth.util.CookieManager;
import com.everypet.auth.util.JWTManager;
import com.everypet.auth.util.TokenExpirationTime;
import com.everypet.member.data.dto.MemberDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StreamUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;

@RequiredArgsConstructor
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final JWTManager jwtManager;
    private final CookieManager cookieManager;

    // access 토큰 만료시간
    private Long accessTime = TokenExpirationTime.ACCESS_TIME;

    // refresh 토큰 만료시간
    private Long refreshTime = TokenExpirationTime.REFRESH_TIME;

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

        MemberDTO memberDTO = new MemberDTO();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ServletInputStream inputStream = request.getInputStream();
            String messageBody = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            memberDTO = objectMapper.readValue(messageBody, MemberDTO.class);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String memberId = memberDTO.getMemberId();
        String memberPwd = memberDTO.getMemberPwd();

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(memberId, memberPwd);

        return authenticationManager.authenticate(authToken);
    }

    // 인증 성공시 실행되는 메소드
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authentication) throws IOException, ServletException {

        // 유저 정보
        String memberId = authentication.getName();

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority auth = iterator.next();
        String role = auth.getAuthority();

        // 토큰 생성
        String access = jwtManager.createJwt("access", memberId, role, accessTime);
        String refresh = jwtManager.createJwt("refresh", memberId, role, refreshTime);

        // Refresh token 저장
        jwtManager.addRefreshToken(memberId, refresh, refreshTime);

        // 응답 설정
        response.setHeader("access", access);
        response.addCookie(cookieManager.createCookie("refresh", refresh));
        response.setStatus(HttpStatus.OK.value());
    }

    // 인증 실패시 실행되는 메소드
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        response.setStatus(401);
    }
}