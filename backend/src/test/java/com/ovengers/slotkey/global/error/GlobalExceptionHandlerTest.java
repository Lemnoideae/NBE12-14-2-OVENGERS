package com.ovengers.slotkey.global.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new TestController()
                )
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .build();
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 전용 오류 코드를 반환한다")
    void shouldReturnIdempotencyKeyRequiredWhenHeaderIsMissing() throws Exception {
        mockMvc.perform(
                        get("/test/idempotency")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value(
                        "IDEMPOTENCY_KEY_REQUIRED"
                ))
                .andExpect(jsonPath("$.message").value(
                        "Idempotency-Key 헤더가 필요합니다."
                ));
    }

    @Test
    @DisplayName("처리되지 않은 예외는 공통 서버 오류 형식으로 반환한다")
    void shouldReturnInternalServerErrorWhenUnexpectedExceptionOccurs() throws Exception {
        mockMvc.perform(
                        get("/test/exception")
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.code").value(
                        "INTERNAL_SERVER_ERROR"
                ))
                .andExpect(jsonPath("$.message").value(
                        "서버 내부 오류가 발생했습니다."
                ));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/idempotency")
        String requireIdempotencyKey(
                @RequestHeader("Idempotency-Key")
                String idempotencyKey
        ) {
            return idempotencyKey;
        }

        @GetMapping("/test/exception")
        String throwUnexpectedException() {
            throw new IllegalStateException(
                    "외부에 노출하면 안 되는 내부 오류"
            );
        }
    }
}
