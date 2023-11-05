package com.plocky.global.jwt.filter;

import com.plocky.domain.member.entity.Member;
import com.plocky.domain.member.repository.MemberRepository;
import com.plocky.global.jwt.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {
    private final String NO_CHECK_URL = "/oauth/kakao/login/uri";
    private final JwtService jwtService;
    private final MemberRepository memberRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().equals(NO_CHECK_URL)) {
            log.info("No check url");
            filterChain.doFilter(request, response);
            return;
        }
        checkAccessTokenAndAuthentication(request, response, filterChain);
    }

    public void checkAccessTokenAndAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException{
        jwtService.extractAccessToken(request).ifPresent(
                accessToken -> {
                    jwtService.extractKakaoId(accessToken).ifPresent(
                            kakaoId -> {
                                memberRepository.findByKakaoId(kakaoId).ifPresent(
                                        member -> saveAuthentication(member)
                                );
                            }
                    );
                }
        );
        filterChain.doFilter(request, response);
    }

    private void saveAuthentication(Member member) {
    }
}
