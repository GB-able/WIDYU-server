package com.widyu.goal.walk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.widyu.global.util.MemberUtil;
import com.widyu.goal.walk.dto.response.WalkMonthlyResponse;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.MemberRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalkService 월별 조회 단위 테스트")
class WalkServiceTest {

    @Mock private WalkRepository walkRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MemberUtil memberUtil;

    @InjectMocks private WalkService walkService;

    @Test
    @DisplayName("기록이 없는 과거 날짜는 기본 목표를 소급 적용하지 않고 오늘·미래만 기본 목표로 채운다")
    void 기록이_없는_과거_날짜는_기본_목표를_소급하지_않는다() {
        // given
        Long memberId = 1L;
        Member member = org.mockito.Mockito.mock(Member.class);
        SeniorProfile seniorProfile = org.mockito.Mockito.mock(SeniorProfile.class);
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(member.getId()).willReturn(memberId);
        given(member.getSeniorProfile()).willReturn(seniorProfile);
        given(seniorProfile.hasDefaultWalkGoal()).willReturn(true);
        given(seniorProfile.getDefaultWalkGoal()).willReturn(5000);
        given(walkRepository.countAchievedGoals(anyLong(), any(), any())).willReturn(0L);
        given(walkRepository.countTotalRecords(anyLong(), any(), any())).willReturn(0L);
        given(walkRepository.findByMemberAndWalkDateBetweenOrderByWalkDateAsc(any(), any(), any()))
                .willReturn(List.of());

        YearMonth currentMonth = YearMonth.now();
        LocalDate today = LocalDate.now();

        // when
        WalkMonthlyResponse response = walkService.getMonthlyStats(
                currentMonth.getYear(), currentMonth.getMonthValue(), memberId);

        // then
        List<LocalDate> dates = response.dailyData().stream()
                .map(daily -> LocalDate.parse(daily.date()))
                .toList();
        assertThat(dates).isNotEmpty();
        assertThat(dates).allSatisfy(date -> assertThat(date).isAfterOrEqualTo(today));

        long expectedDays = currentMonth.atEndOfMonth().getDayOfMonth() - today.getDayOfMonth() + 1;
        assertThat(dates).hasSize((int) expectedDays);
    }
}
