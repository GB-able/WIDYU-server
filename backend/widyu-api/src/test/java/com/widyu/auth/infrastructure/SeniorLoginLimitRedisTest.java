package com.widyu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.widyu.auth.application.senior.SeniorAuthService;
import com.widyu.auth.dto.request.SeniorSignInRequest;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.auth.exception.AuthRateLimitException;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.properties.AuthLimitProperties;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.addressbookmark.application.GeocodingService;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeniorLoginLimitRedisTest {
    @Mock private MemberRepository members;
    @Mock private SeniorProfileRepository profiles;
    @Mock private FamilyRepository families;
    @Mock private FamilyMembershipRepository memberships;
    @Mock private ParentLocationRepository locations;
    @Mock private GeocodingService geocoding;
    @Mock private JwtTokenProvider tokens;
    @Mock private MemberUtil memberUtil;
    @Mock private ClientIpResolver ip;

    private SeniorAuthService service(AuthRedisFixture fixture, int ipFailures) {
        given(ip.resolve()).willReturn("192.0.2.1");
        var store = new AuthLimitStore(fixture.redis,
                new AuthLimitProperties(60, 3, 10, 30, 1000, 900, 10, ipFailures));
        return new SeniorAuthService(members, profiles, families, memberships, locations, geocoding, tokens,
                memberUtil, store, ip);
    }

    private SeniorProfile profile(String phone, String inviteCode) {
        var member = Member.createMember(MemberType.SENIOR, "synthetic", phone);
        ReflectionTestUtils.setField(member, "id", 2L);
        return SeniorProfile.createSeniorProfile(member, Family.createFamily("ABC123"), "synthetic", inviteCode,
                LocalDate.of(1950, 1, 1));
    }

    @Test
    @DisplayName("같은 전화번호로 틀린 초대코드를 열 번 보내면 다음 요청은 맞는 코드여도 조회 전에 거부한다")
    void 같은_전화번호로_열_번_실패하면_맞는_코드도_거부한다() throws Exception {
        // given
        try (var fixture = new AuthRedisFixture()) {
            var service = service(fixture, 100);
            given(profiles.findAllByInviteCodeAndMemberPhoneNumber(anyString(), anyString())).willReturn(List.of());

            // when
            for (int i = 0; i < 10; i++) {
                var wrong = new SeniorSignInRequest(String.format("%07d", i), "01000000001");
                assertThatThrownBy(() -> service.seniorSignIn(wrong)).isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_CODE_NOT_FOUND);
            }

            // then
            assertThatThrownBy(() -> service.seniorSignIn(new SeniorSignInRequest("1234567", "01000000001")))
                    .isInstanceOf(AuthRateLimitException.class);
            verify(profiles, times(10)).findAllByInviteCodeAndMemberPhoneNumber(anyString(), anyString());
            verifyNoInteractions(tokens);
        }
    }

    @Test
    @DisplayName("같은 IP에서 여러 전화번호로 실패가 IP 한도에 닿으면 다른 전화번호의 로그인도 거부한다")
    void 같은_IP의_실패가_한도에_닿으면_다른_번호도_거부한다() throws Exception {
        // given
        try (var fixture = new AuthRedisFixture()) {
            var service = service(fixture, 3);
            given(profiles.findAllByInviteCodeAndMemberPhoneNumber(anyString(), anyString())).willReturn(List.of());

            // when
            for (int i = 0; i < 3; i++) {
                var wrong = new SeniorSignInRequest("0000000", "0100000000" + i);
                assertThatThrownBy(() -> service.seniorSignIn(wrong))
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_CODE_NOT_FOUND);
            }

            // then
            assertThatThrownBy(() -> service.seniorSignIn(new SeniorSignInRequest("0000000", "01000000009")))
                    .isInstanceOf(AuthRateLimitException.class);
        }
    }

    @Test
    @DisplayName("맞는 초대코드로 한도보다 많이 로그인해도 매번 토큰 쌍을 받고 실패로 세지 않는다")
    void 성공한_로그인은_실패_한도를_쓰지_않는다() throws Exception {
        // given
        try (var fixture = new AuthRedisFixture()) {
            var service = service(fixture, 100);
            given(profiles.findAllByInviteCodeAndMemberPhoneNumber("1234567", "01000000001"))
                    .willReturn(List.of(profile("01000000001", "1234567")));
            var expected = TokenPairResponse.of(2L, "synthetic-access", "synthetic-refresh");
            given(tokens.generateTokenPair(any(), any(), anyString())).willReturn(expected);

            // when / then
            for (int i = 0; i < 15; i++) {
                assertThat(service.seniorSignIn(new SeniorSignInRequest("1234567", "01000000001")))
                        .isEqualTo(expected);
            }
        }
    }
}
