package com.widyu.location.access.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.location.access.LocationAccessLog;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.access.dto.response.LocationAccessExportFile;
import com.widyu.location.access.repository.LocationAccessLogRepository;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationAccessExportService 위치 열람 기록 CSV 내보내기 테스트")
class LocationAccessExportServiceTest {

    private static final Long SENIOR_ID = 28L;
    private static final Long VIEWER_ID = 55L;
    private static final Long LEFT_VIEWER_ID = 77L;
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 1, 0, 0);

    @Mock private MemberRepository memberRepository;
    @Mock private LocationAccessLogRepository locationAccessLogRepository;
    @InjectMocks private LocationAccessExportService service;

    @Test
    @DisplayName("기간을 주면 그 어르신의 열람 기록을 보호자 이름과 통보 여부를 담은 CSV로 돌려준다")
    void 기간을_주면_열람_기록을_보호자_이름과_통보_여부를_담은_CSV로_돌려준다() {
        // given
        LocationAccessLog notified = LocationAccessLog.of(
                VIEWER_ID, SENIOR_ID, LocationAccessPath.REST_LAST, LocalDateTime.of(2026, 9, 21, 10, 12, 3));
        notified.markNotified(LocalDateTime.of(2026, 9, 21, 10, 12, 4));
        LocationAccessLog merged = LocationAccessLog.of(
                LEFT_VIEWER_ID, SENIOR_ID, LocationAccessPath.WS_SUBSCRIBE, LocalDateTime.of(2026, 9, 22, 8, 0));
        given(memberRepository.existsById(SENIOR_ID)).willReturn(true);
        given(locationAccessLogRepository.findBySeniorMemberIdAndAccessedAtBetweenOrderByAccessedAtAscIdAsc(
                SENIOR_ID, FROM, TO)).willReturn(List.of(notified, merged));
        given(memberRepository.findAllById(List.of(VIEWER_ID, LEFT_VIEWER_ID))).willReturn(List.of(guardian()));

        // when
        LocationAccessExportFile file = service.export(SENIOR_ID, FROM, TO);

        // then
        assertThat(file.fileName()).isEqualTo("location_access_member28_20260801T0000-20261101T0000.csv");
        assertThat(new String(file.bytes(), UTF_8)).isEqualTo(
                "﻿열람 시각(한국 시간),열람 시각(epoch ms),보호자 회원 ID,보호자 이름,경로 코드,경로,통보 여부,"
                        + "통보 시각(한국 시간)\n"
                        + "2026-09-21 10:12:03,1789953123000,55,김보호,REST_LAST,최근 위치 조회,예,2026-09-21 10:12:04\n"
                        + "2026-09-22 08:00:00,1790031600000,77,,WS_SUBSCRIBE,실시간 위치 보기 시작,아니오,\n");
    }

    @Test
    @DisplayName("기록이 없으면 머리글만 있는 CSV를 돌려준다")
    void 기록이_없으면_머리글만_있는_CSV를_돌려준다() {
        // given
        given(memberRepository.existsById(SENIOR_ID)).willReturn(true);
        given(locationAccessLogRepository.findBySeniorMemberIdAndAccessedAtBetweenOrderByAccessedAtAscIdAsc(
                SENIOR_ID, FROM, TO)).willReturn(List.of());

        // when
        LocationAccessExportFile file = service.export(SENIOR_ID, FROM, TO);

        // then
        assertThat(new String(file.bytes(), UTF_8)).isEqualTo(LocationAccessLogCsv.BOM + LocationAccessLogCsv.HEADER);
    }

    @Test
    @DisplayName("시작이 끝보다 늦거나 같으면 기간 오류 예외가 발생하고 저장소를 읽지 않는다")
    void 시작이_끝보다_늦거나_같으면_기간_오류_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> service.export(SENIOR_ID, TO, TO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOCATION_ACCESS_EXPORT_RANGE_INVALID);
        verifyNoInteractions(memberRepository, locationAccessLogRepository);
    }

    @Test
    @DisplayName("기간이 1년을 넘으면 기간 오류 예외가 발생한다")
    void 기간이_1년을_넘으면_기간_오류_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> service.export(SENIOR_ID, FROM, FROM.plusYears(1).plusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOCATION_ACCESS_EXPORT_RANGE_INVALID);
        verifyNoInteractions(locationAccessLogRepository);
    }

    @Test
    @DisplayName("기간이 정확히 1년이면 내보낸다")
    void 기간이_정확히_1년이면_내보낸다() {
        // given
        LocalDateTime to = FROM.plusYears(1);
        given(memberRepository.existsById(SENIOR_ID)).willReturn(true);
        given(locationAccessLogRepository.findBySeniorMemberIdAndAccessedAtBetweenOrderByAccessedAtAscIdAsc(
                SENIOR_ID, FROM, to)).willReturn(List.of());

        // when
        LocationAccessExportFile file = service.export(SENIOR_ID, FROM, to);

        // then
        assertThat(file.fileName()).isEqualTo("location_access_member28_20260801T0000-20270801T0000.csv");
    }

    @Test
    @DisplayName("없는 회원이면 회원 없음 예외가 발생하고 열람 기록을 읽지 않는다")
    void 없는_회원이면_회원_없음_예외가_발생한다() {
        // given
        given(memberRepository.existsById(SENIOR_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> service.export(SENIOR_ID, FROM, TO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        verifyNoInteractions(locationAccessLogRepository);
    }

    private Member guardian() {
        Member member = Member.createMember(MemberType.GUARDIAN, "김보호", "01011112222");
        ReflectionTestUtils.setField(member, "id", VIEWER_ID);
        return member;
    }
}
