package com.ovengers.slotkey.space.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ovengers.slotkey.global.error.GlobalExceptionHandler;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.member.entity.MemberRole;
import com.ovengers.slotkey.space.dto.request.SpaceCreateRequest;
import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import com.ovengers.slotkey.space.dto.response.SpaceDetailResponse;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.service.AdminSpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminSpaceControllerTest {

        private MockMvc mockMvc;

    @Mock
    private AdminSpaceService adminSpaceService;

    @InjectMocks
    private AdminSpaceController adminSpaceController;

    private ObjectMapper objectMapper;

    private static final AuthPrincipal ADMIN_PRINCIPAL = new AuthPrincipal(1L, "admin@test.com", MemberRole.ADMIN);

    @BeforeEach
    void setUp() {
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(adminSpaceController)
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        // @AuthenticationPrincipal 주입을 위해 SecurityContext에 인증 정보 설정
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(ADMIN_PRINCIPAL, null,
                        ADMIN_PRINCIPAL.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
}

    @Test
    @DisplayName("공간 등록 요청 시 201 Created와 ApiResponse 규격 응답을 반환한다")
    void createSpace_returnsCreated() throws Exception {
            // given
            SpaceCreateRequest request = new SpaceCreateRequest(
                            "회의실 1",
                            "서울시 강남구",
                            "깔끔한 회의실",
                            6,
                            5000L,
                            "/img.jpg",
                            LocalTime.of(9, 0),
                            LocalTime.of(18, 0));

        SpaceDetailResponse response = SpaceDetailResponse.builder()
                        .id(1L)
                        .name(request.name())
                        .location(request.location())
                        .description(request.description())
                        .capacity(request.capacity())
                        .pricePerSlot(request.pricePerSlot())
                        .imagePath(request.imagePath())
                        .openingTime(request.openingTime())
                        .closingTime(request.closingTime())
                        .status(SpaceStatus.ACTIVE)
                        .version(0)
                        .build();

        given(adminSpaceService.createSpace(any(SpaceCreateRequest.class), eq(1L))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("SUCCESS"))
                        .andExpect(jsonPath("$.code").value("OK"))
                        .andExpect(jsonPath("$.data.id").value(1L))
                        .andExpect(jsonPath("$.data.name").value("회의실 1"))
                        .andExpect(jsonPath("$.data.version").value(0));
}

    @Test
    @DisplayName("필수 필드(이름 누락 등) 유효성 검증 실패 시 400 Bad Request를 반환한다")
    void createSpace_validationFailed() throws Exception {
            // name이 빈 문자열인 요청
            SpaceCreateRequest invalidRequest = new SpaceCreateRequest(
                            "",
                            "서울시 강남구",
                            "설명",
                            6,
                            5000L,
                            null,
                            LocalTime.of(9, 0),
                            LocalTime.of(18, 0));

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value("FAIL"))
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
}

    @Test
    @DisplayName("공간 수정 요청 시 200 OK와 수정된 SpaceDetailResponse를 반환한다")
    void updateSpace_returnsOk() throws Exception {
            // given
            SpaceUpdateRequest request = new SpaceUpdateRequest(
                            1L,
                            "수정된 회의실",
                            "서울시 서초구",
                            "업데이트된 설명",
                            8,
                            7000L,
                            "/new-img.jpg",
                            LocalTime.of(8, 0),
                            LocalTime.of(20, 0),
                            SpaceStatus.ACTIVE);

            SpaceDetailResponse response = SpaceDetailResponse.builder()
                            .id(1L)
                            .name(request.name())
                            .location(request.location())
                            .description(request.description())
                            .capacity(request.capacity())
                            .pricePerSlot(request.pricePerSlot())
                            .imagePath(request.imagePath())
                            .openingTime(request.openingTime())
                            .closingTime(request.closingTime())
                            .status(SpaceStatus.ACTIVE)
                            .version(1)
                            .build();

            given(adminSpaceService.updateSpace(eq(1L), any(SpaceUpdateRequest.class), eq(1L))).willReturn(response);

            // when & then
            mockMvc.perform(patch("/api/v1/admin/spaces/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("SUCCESS"))
                            .andExpect(jsonPath("$.code").value("OK"))
                            .andExpect(jsonPath("$.data.id").value(1L))
                            .andExpect(jsonPath("$.data.name").value("수정된 회의실"))
                            .andExpect(jsonPath("$.data.version").value(1));
    }
}
