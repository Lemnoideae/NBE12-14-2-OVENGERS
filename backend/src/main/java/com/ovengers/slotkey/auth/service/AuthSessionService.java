package com.ovengers.slotkey.auth.service;

import com.ovengers.slotkey.auth.entity.AuthSession;
import com.ovengers.slotkey.auth.repository.AuthSessionRepository;
import com.ovengers.slotkey.auth.repository.RefreshTokenRepository;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import jakarta.persistence.PersistenceException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionService.class);

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Transactional
    public AuthSession create(Member member) {
        return authSessionRepository.save(
                new AuthSession(member, LocalDateTime.now(clock))
        );
    }

    public ValidationResult validate(String sessionId, Long memberId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ValidationResult.INVALID;
        }

        try {
            LocalDateTime now =
                    LocalDateTime.now(clock);

            boolean usable = authSessionRepository.isUsable(
                    sessionId,
                    memberId,
                    now,
                    MemberStatus.ACTIVE
            );

            if (usable) {
                return ValidationResult.VALID;
            }

            // 인증 실패 시 세션 소유자의 현재 비활성 여부 확인
            boolean inactiveMember = authSessionRepository
                    .findMemberStatusForAccessAttempt(
                            sessionId,
                            memberId,
                            now
                    )
                    .filter(status -> status != MemberStatus.ACTIVE)
                    .isPresent();

            if (inactiveMember) {
                return ValidationResult.MEMBER_INACTIVE;
            }

            return ValidationResult.INVALID;
        } catch (DataAccessException | PersistenceException | TransactionException failure) {
            log.error(
                    "Authentication session lookup failed; rejecting request (causeType={})",
                    failure.getClass().getSimpleName()
            );

            return ValidationResult.UNAVAILABLE;
        }
    }

    @Transactional
    public void revoke(AuthSession session, LocalDateTime now) {
        session.revoke(now);
    }

    @Transactional
    public void revokeAllForMember(Long memberId, LocalDateTime now) {
        authSessionRepository.revokeAllByMemberId(memberId, now);
        refreshTokenRepository.revokeAllByMemberId(memberId, now);
    }

    public enum ValidationResult {
        VALID,
        INVALID,
        MEMBER_INACTIVE,
        UNAVAILABLE
    }
}
