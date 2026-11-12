package com.widyu.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.member.MemberWithdrawalRequest;
import com.widyu.member.MemberWithdrawalRequestStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@DisplayName("어르신 탈퇴 요청 조회")
class MemberWithdrawalRequestRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Autowired private MemberWithdrawalRequestRepository repository;

    @Test
    @DisplayName("회원의 열린 요청을 찾으면 처리 완료된 요청은 빼고 처리 대기 요청만 돌려준다")
    void 회원의_열린_요청을_찾으면_처리_대기_요청만_돌려준다() {
        // given
        MemberWithdrawalRequest processed = MemberWithdrawalRequest.request(7L, NOW);
        processed.complete(1L, NOW.plusDays(1));
        repository.saveAndFlush(processed);
        MemberWithdrawalRequest open = repository.saveAndFlush(MemberWithdrawalRequest.request(7L, NOW.plusDays(2)));
        repository.saveAndFlush(MemberWithdrawalRequest.request(8L, NOW));

        // when & then
        assertThat(repository.findFirstByMemberIdAndStatus(7L, MemberWithdrawalRequestStatus.REQUESTED))
                .get().extracting(MemberWithdrawalRequest::getId).isEqualTo(open.getId());
        assertThat(repository.findByIdForUpdate(open.getId())).isPresent();
    }

    @Test
    @DisplayName("상태로 거르면 그 상태의 요청만 오래된 순서로 돌려준다")
    void 상태로_거르면_그_상태의_요청만_오래된_순서로_돌려준다() {
        // given
        MemberWithdrawalRequest first = repository.saveAndFlush(MemberWithdrawalRequest.request(5L, NOW));
        MemberWithdrawalRequest cancelled = MemberWithdrawalRequest.request(6L, NOW);
        cancelled.cancel(1L, NOW.plusDays(1));
        repository.saveAndFlush(cancelled);
        MemberWithdrawalRequest second = repository.saveAndFlush(MemberWithdrawalRequest.request(7L, NOW));

        // when
        var page = repository.findByStatusOrderByIdAsc(MemberWithdrawalRequestStatus.REQUESTED, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).extracting(MemberWithdrawalRequest::getId)
                .containsExactly(first.getId(), second.getId());
    }
}
