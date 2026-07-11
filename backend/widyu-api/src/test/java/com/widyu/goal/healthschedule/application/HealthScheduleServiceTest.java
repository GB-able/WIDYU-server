package com.widyu.goal.healthschedule.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.healthschedule.dto.request.HealthScheduleCreateForSeniorRequest;
import com.widyu.goal.healthschedule.dto.request.HealthScheduleUpdateRequest;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthScheduleService 예외 처리 단위 테스트")
class HealthScheduleServiceTest {

    @Mock private HealthScheduleRepository healthScheduleRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private MemberUtil memberUtil;

    @InjectMocks
    private HealthScheduleService healthScheduleService;

    @Test
    @DisplayName("보호자가 존재하지 않는 시니어 일정 생성 시 BAD_REQUEST 예외를 던지고 저장하지 않는다")
    void 존재하지_않는_시니어_일정_생성_시_예외가_발생한다() {
        // given
        Member guardian = member(1L, MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(memberRepository.findById(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> healthScheduleService.createHealthScheduleForSenior(createRequest(2L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("시니어를 찾을 수 없습니다.");
        then(healthScheduleRepository).should(never()).save(any(HealthSchedule.class));
    }

    @Test
    @DisplayName("보호자가 프로필 없는 시니어 일정 생성 시 SENIOR_PROFILE_NOT_FOUND 예외를 던지고 저장하지 않는다")
    void 프로필_없는_시니어_일정_생성_시_예외가_발생한다() {
        // given
        Member guardian = member(1L, MemberType.GUARDIAN);
        Member senior = member(2L, MemberType.SENIOR);
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(memberRepository.findById(2L)).willReturn(Optional.of(senior));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> healthScheduleService.createHealthScheduleForSenior(createRequest(2L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENIOR_PROFILE_NOT_FOUND)
                .hasMessageContaining("시니어 프로필을 찾을 수 없습니다.");
        then(healthScheduleRepository).should(never()).save(any(HealthSchedule.class));
    }

    @Test
    @DisplayName("연결되지 않은 보호자가 시니어 일정 생성 시 FORBIDDEN 예외를 던지고 저장하지 않는다")
    void 연결되지_않은_보호자_일정_생성_시_예외가_발생한다() {
        // given
        Member guardian = member(1L, MemberType.GUARDIAN);
        Member senior = member(2L, MemberType.SENIOR);
        SeniorProfile seniorProfile = seniorProfile(10L, senior);
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(memberRepository.findById(2L)).willReturn(Optional.of(senior));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> healthScheduleService.createHealthScheduleForSenior(createRequest(2L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("해당 시니어의 일정을 생성할 권한이 없습니다.");
        then(healthScheduleRepository).should(never()).save(any(HealthSchedule.class));
    }

    @Test
    @DisplayName("시니어가 타인 일정 수정 시 FORBIDDEN 예외를 던진다")
    void 시니어가_타인_일정_수정_시_예외가_발생한다() {
        // given
        Member currentSenior = member(1L, MemberType.SENIOR);
        Member otherSenior = member(2L, MemberType.SENIOR);
        HealthSchedule schedule = schedule(otherSenior);
        given(memberUtil.getCurrentMember()).willReturn(currentSenior);
        given(healthScheduleRepository.findById(100L)).willReturn(Optional.of(schedule));

        // when & then
        assertThatThrownBy(() -> healthScheduleService.updateHealthSchedule(100L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("해당 일정에 접근할 권한이 없습니다.");
    }

    @Test
    @DisplayName("보호자가 연결되지 않은 시니어의 날짜별 일정 조회 시 FORBIDDEN 예외를 던진다")
    void 보호자가_연결되지_않은_시니어_날짜별_조회_시_예외가_발생한다() {
        // given
        Member guardian = member(1L, MemberType.GUARDIAN);
        Member senior = member(2L, MemberType.SENIOR);
        SeniorProfile seniorProfile = seniorProfile(10L, senior);
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> healthScheduleService.getHealthSchedulesByDateForSenior(2L, LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("해당 시니어의 일정을 조회할 권한이 없습니다.");
    }

    private HealthScheduleCreateForSeniorRequest createRequest(Long memberId) {
        return new HealthScheduleCreateForSeniorRequest(
                memberId, "병원 방문", "서울시 강남구", 37.5, 127.0, LocalDateTime.now().plusDays(1));
    }

    private HealthScheduleUpdateRequest updateRequest() {
        return new HealthScheduleUpdateRequest("수정", "서울시 서초구", 37.4, 127.1,
                LocalDateTime.now().plusDays(2), ProgressStatus.UPCOMING);
    }

    private Member member(Long id, MemberType type) {
        Member member = Member.createMember(type, type.name(), "01011112222");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private SeniorProfile seniorProfile(Long id, Member member) {
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                member, Family.createFamily("ABC123"), "서울시", "INV1234", LocalDate.of(1950, 1, 1));
        ReflectionTestUtils.setField(seniorProfile, "id", id);
        ReflectionTestUtils.setField(member, "seniorProfile", seniorProfile);
        return seniorProfile;
    }

    private HealthSchedule schedule(Member member) {
        return HealthSchedule.create(member, "병원 방문", "서울시", 37.5, 127.0, LocalDateTime.now().plusDays(1));
    }
}
