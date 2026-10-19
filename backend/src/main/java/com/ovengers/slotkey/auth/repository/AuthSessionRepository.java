package com.ovengers.slotkey.auth.repository;

import com.ovengers.slotkey.auth.entity.AuthSession;
import com.ovengers.slotkey.member.entity.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :sessionId")
    Optional<AuthSession> findByIdForUpdate(@Param("sessionId") String sessionId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AuthSession s
            set s.revokedAt = :now
            where s.member.id = :memberId
              and s.revokedAt is null
            """)
    int revokeAllByMemberId(
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select (count(s) > 0)
            from AuthSession s join s.member m
            where s.id = :sessionId
              and s.revokedAt is null
              and s.expiresAt > :now
              and m.id = :memberId
              and m.status = :activeStatus
            """)
    boolean isUsable(
            @Param("sessionId") String sessionId,
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now,
            @Param("activeStatus") MemberStatus activeStatus
    );

    // 세션 소유자가 일치하고 만료되지 않은 경우 현재 회원 상태 조회
    // 정지 처리 시 세션도 폐기되므로 revokedAt 조건은 적용하지 않음
    @Query("""
            select m.status
            from AuthSession s join s.member m
            where s.id = :sessionId
              and m.id = :memberId
              and s.expiresAt > :now
            """)
    Optional<MemberStatus> findMemberStatusForAccessAttempt(
            @Param("sessionId") String sessionId,
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now
    );
}
