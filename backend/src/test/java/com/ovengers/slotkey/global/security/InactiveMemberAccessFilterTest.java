package com.ovengers.slotkey.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.slotkey.access.service.InactiveMemberAccessLogService;
import com.ovengers.slotkey.auth.service.AuthSessionService;
import com.ovengers.slotkey.global.security.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InactiveMemberAccessFilterTest {

    private static final Long MEMBER_ID = 1L;
    private static final String SESSION_ID = "test-session";
    private static final String ACCESS_TOKEN = "test-access-token";

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-19T07:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private InactiveMemberAccessLogService inactiveMemberAccessLogService;

    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CustomAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        filter = new CustomAuthenticationFilter(
                jwtProvider,
                objectMapper,
                authSessionService,
                inactiveMemberAccessLogService,
                CLOCK
        );
    }

    @Test
    @DisplayName("비활성 회원의 출입 검증 요청은 로그를 기록하고 401로 차단한다")
    void shouldRecordAndRejectInactiveMemberAccess() throws Exception {
        givenSessionResult(
                AuthSessionService.ValidationResult.MEMBER_INACTIVE
        );

        MockHttpServletRequest request = createRequest(
                "POST",
                "/api/v1/door-access/verify"
        );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertUnauthorized(response);

        verify(inactiveMemberAccessLogService).record(
                MEMBER_ID,
                LocalDateTime.now(CLOCK)
        );

        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("출입 로그 저장이 실패해도 비활성 회원의 출입은 차단한다")
    void shouldRejectAccessWhenLogRecordingFails() throws Exception {
        givenSessionResult(
                AuthSessionService.ValidationResult.MEMBER_INACTIVE
        );

        doThrow(new IllegalStateException("로그 저장 실패"))
                .when(inactiveMemberAccessLogService)
                .record(
                        MEMBER_ID,
                        LocalDateTime.now(CLOCK)
                );

        MockHttpServletRequest request = createRequest(
                "POST",
                "/api/v1/door-access/verify"
        );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertUnauthorized(response);

        assertThat(response.getContentAsString())
                .doesNotContain("로그 저장 실패");

        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("비활성 회원의 일반 조회 요청에는 출입 로그를 남기지 않는다")
    void shouldNotRecordForGeneralRequest() throws Exception {
        givenSessionResult(
                AuthSessionService.ValidationResult.MEMBER_INACTIVE
        );

        MockHttpServletRequest request = createRequest(
                "GET",
                "/api/v1/members/me"
        );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertUnauthorized(response);

        verifyNoInteractions(
                inactiveMemberAccessLogService,
                filterChain
        );
    }

    @Test
    @DisplayName("비활성 회원의 출입키 발급 요청에는 출입 로그를 남기지 않는다")
    void shouldNotRecordForTokenIssueRequest() throws Exception {
        givenSessionResult(
                AuthSessionService.ValidationResult.MEMBER_INACTIVE
        );

        MockHttpServletRequest request = createRequest(
                "POST",
                "/api/v1/reservations/1/door-token"
        );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertUnauthorized(response);

        verifyNoInteractions(
                inactiveMemberAccessLogService,
                filterChain
        );
    }

    @Test
    @DisplayName("JWT 검증에 실패한 출입 요청은 회원 비활성으로 기록하지 않는다")
    void shouldNotRecordWhenJwtValidationFails() throws Exception {
        given(jwtProvider.payloadOrNull(ACCESS_TOKEN))
                .willReturn(null);

        MockHttpServletRequest request = createRequest(
                "POST",
                "/api/v1/door-access/verify"
        );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertUnauthorized(response);

        verifyNoInteractions(
                authSessionService,
                inactiveMemberAccessLogService,
                filterChain
        );
    }

    @Test
    @DisplayName("일반 세션 인증 실패는 회원 비활성으로 기록하지 않는다")
    void shouldNotRecordWhenSessionIsInvalid() throws Exception {
        givenSessionResult(
                AuthSessionService.ValidationResult.INVALID
        );

        MockHttpServletRequest request = createRequest(
                "POST",
                "/api/v1/door-access/verify"
        );
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertUnauthorized(response);

        verifyNoInteractions(
                inactiveMemberAccessLogService,
                filterChain
        );
    }

    private void givenSessionResult(
            AuthSessionService.ValidationResult result
    ) {
        given(jwtProvider.payloadOrNull(ACCESS_TOKEN))
                .willReturn(Map.<String, Object>of(
                        "id", MEMBER_ID,
                        "email", "member@test.com",
                        "role", "USER",
                        "sid", SESSION_ID,
                        "exp", CLOCK.instant().plusSeconds(300).getEpochSecond()
                ));

        given(authSessionService.validate(
                SESSION_ID,
                MEMBER_ID
        )).willReturn(result);
    }

    private MockHttpServletRequest createRequest(
            String method,
            String path
    ) {
        MockHttpServletRequest request =
                new MockHttpServletRequest(method, path);

        request.setServletPath(path);

        request.addHeader(
                "Authorization",
                "Bearer " + ACCESS_TOKEN
        );

        return request;
    }

    private void assertUnauthorized(
            MockHttpServletResponse response
    ) throws Exception {
        assertThat(response.getStatus()).isEqualTo(401);

        var body = objectMapper.readTree(
                response.getContentAsString()
        );

        assertThat(body.path("status").asText())
                .isEqualTo("FAIL");

        assertThat(body.path("code").asText())
                .isEqualTo("INVALID_ACCESS_TOKEN");

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
    }
}