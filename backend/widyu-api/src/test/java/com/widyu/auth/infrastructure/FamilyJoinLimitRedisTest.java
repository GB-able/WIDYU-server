package com.widyu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.widyu.auth.exception.AuthRateLimitException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.properties.AuthLimitProperties;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.application.FamilyConnectionService;
import com.widyu.member.dto.request.FamilyJoinRequest;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FamilyJoinLimitRedisTest {
    @Mock private MemberUtil memberUtil;
    @Mock private FamilyRepository families;
    @Mock private FamilyMembershipRepository memberships;
    @Mock private SeniorProfileRepository seniors;
    @Mock private ClientIpResolver ip;

    private AuthLimitStore store(AuthRedisFixture fixture, int ipFailures) {
        return new AuthLimitStore(fixture.redis, new AuthLimitProperties(60, 3, 10, 30, 1000, 900, 10, ipFailures));
    }

    private FamilyConnectionService service(AuthLimitStore store) {
        given(ip.resolve()).willReturn("192.0.2.1");
        return new FamilyConnectionService(memberUtil, families, memberships, seniors, store, ip);
    }

    private Member guardian(long id) {
        Member member = Member.createMember(MemberType.GUARDIAN, "synthetic", "01000000000");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("같은 보호자가 틀린 가족 코드를 열 번 보내면 다음 요청은 코드 조회 전에 거부한다")
    void 같은_보호자가_열_번_틀리면_다음_요청을_거부한다() throws Exception {
        // given
        try (var fixture = new AuthRedisFixture()) {
            var service = service(store(fixture, 100));
            given(memberUtil.getCurrentMember()).willReturn(guardian(1L));
            given(families.findByFamilyCode(anyString())).willReturn(Optional.empty());

            // when
            for (int i = 0; i < 10; i++) {
                var wrong = new FamilyJoinRequest(String.format("AAAA%02d", i));
                assertThatThrownBy(() -> service.joinFamily(wrong))
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_CODE_NOT_FOUND);
            }

            // then
            assertThatThrownBy(() -> service.joinFamily(new FamilyJoinRequest("ABC123")))
                    .isInstanceOf(AuthRateLimitException.class);
            verify(families, times(10)).findByFamilyCode(anyString());
        }
    }

    @Test
    @DisplayName("같은 IP에서 여러 보호자의 실패가 IP 한도에 닿으면 다른 보호자의 참여도 거부한다")
    void 같은_IP의_실패가_한도에_닿으면_다른_보호자도_거부한다() throws Exception {
        // given
        try (var fixture = new AuthRedisFixture()) {
            var service = service(store(fixture, 3));
            given(families.findByFamilyCode(anyString())).willReturn(Optional.empty());

            // when
            for (long id = 1; id <= 3; id++) {
                given(memberUtil.getCurrentMember()).willReturn(guardian(id));
                assertThatThrownBy(() -> service.joinFamily(new FamilyJoinRequest("AAAAAA")))
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_CODE_NOT_FOUND);
            }

            // then
            given(memberUtil.getCurrentMember()).willReturn(guardian(4L));
            assertThatThrownBy(() -> service.joinFamily(new FamilyJoinRequest("AAAAAA")))
                    .isInstanceOf(AuthRateLimitException.class);
        }
    }

    @Test
    @DisplayName("같은 IP의 로그인 실패가 IP 한도에 닿아도 가족 참여는 자기 한도로 따로 센다")
    void 로그인_실패는_가족_참여_한도에_섞이지_않는다() throws Exception {
        // given
        try (var fixture = new AuthRedisFixture()) {
            var store = store(fixture, 3);
            for (int i = 0; i < 3; i++) {
                store.completeLogin(store.reserveLogin("unknown:user" + i + "@example.com", "192.0.2.1"), false);
            }
            assertThatThrownBy(() -> store.reserveLogin("unknown:other@example.com", "192.0.2.1"))
                    .isInstanceOf(AuthRateLimitException.class);
            var service = service(store);
            given(memberUtil.getCurrentMember()).willReturn(guardian(1L));
            given(families.findByFamilyCode("AAAAAA")).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> service.joinFamily(new FamilyJoinRequest("AAAAAA")))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_CODE_NOT_FOUND);
        }
    }
}
