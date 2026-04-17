package com.widyu.auth.application.senior;

import com.widyu.auth.dto.request.SeniorSignInRequest;
import com.widyu.auth.dto.request.SeniorSignUpRequest;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeniorAuthService {

    private static final String FAMILY_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int FAMILY_CODE_LENGTH = 6;
    private static final int FAMILY_CODE_MAX_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MemberRepository memberRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final FamilyConnectionRepository familyConnectionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberUtil memberUtil;

    @Transactional
    public void seniorSignUpBulk(List<SeniorSignUpRequest> requests) {
        Member guardian = memberUtil.getCurrentMember();

        validateRequestsNotEmpty(requests);

        List<Member> members = buildMembersFromRequests(requests);
        saveAllMembers(members);

        List<SeniorProfile> profiles = buildProfilesFromRequests(requests, members);
        saveAllProfiles(profiles);

        List<FamilyConnection> connections = buildConnectionsFromRequests(requests, profiles, guardian);
        saveAllConnections(connections);
    }

    @Transactional
    public TokenPairResponse seniorSignIn(SeniorSignInRequest request) {
        SeniorProfile seniorProfile = findByInviteCodeAndPhoneNumber(request.inviteCode(), request.phoneNumber());
        return generateTokenPairForMember(seniorProfile.getMember());
    }

    private void validateRequestsNotEmpty(List<SeniorSignUpRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ": 요청 리스트가 비어 있습니다.");
        }
    }

    private List<Member> buildMembersFromRequests(List<SeniorSignUpRequest> requests) {
        return requests.stream()
                .map(req -> Member.createMember(
                        MemberType.SENIOR,
                        req.name(),
                        req.phoneNumber()
                ))
                .toList();
    }

    private void saveAllMembers(List<Member> members) {
        memberRepository.saveAll(members);
    }

    private List<SeniorProfile> buildProfilesFromRequests(List<SeniorSignUpRequest> requests, List<Member> members) {
        Set<String> usedCodesInBatch = new HashSet<>();
        List<SeniorProfile> profiles = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            SeniorSignUpRequest req = requests.get(i);
            Member member = members.get(i);
            String familyCode = generateUniqueFamilyCode(usedCodesInBatch);
            usedCodesInBatch.add(familyCode);
            SeniorProfile profile = SeniorProfile.createSeniorProfile(
                    member,
                    null,
                    req.address(),
                    req.detailAddress(),
                    req.inviteCode(),
                    familyCode
            );
            profiles.add(profile);
        }
        return profiles;
    }

    private String generateUniqueFamilyCode(Set<String> usedInBatch) {
        for (int attempt = 0; attempt < FAMILY_CODE_MAX_ATTEMPTS; attempt++) {
            String code = generateCode();
            if (!usedInBatch.contains(code) && !seniorProfileRepository.existsByFamilyCode(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "가족코드 생성에 실패했습니다.");
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(FAMILY_CODE_LENGTH);
        for (int i = 0; i < FAMILY_CODE_LENGTH; i++) {
            sb.append(FAMILY_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(FAMILY_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private void saveAllProfiles(List<SeniorProfile> profiles) {
        seniorProfileRepository.saveAll(profiles);
    }

    private List<FamilyConnection> buildConnectionsFromRequests(
            List<SeniorSignUpRequest> requests,
            List<SeniorProfile> profiles,
            Member guardian) {
        List<FamilyConnection> connections = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            SeniorProfile profile = profiles.get(i);

            FamilyConnection connection = FamilyConnection.createLeaderConnection(
                    profile,
                    guardian
            );
            connections.add(connection);
        }
        return connections;
    }

    private void saveAllConnections(List<FamilyConnection> connections) {
        familyConnectionRepository.saveAll(connections);
    }

    private SeniorProfile findByInviteCodeAndPhoneNumber(String inviteCode, String phoneNumber) {
        return seniorProfileRepository.findByInviteCodeAndMemberPhoneNumber(inviteCode, phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND, inviteCode));
    }

    private TokenPairResponse generateTokenPairForMember(Member member) {
        // 시니어는 초대코드로 로그인하므로 "senior"를 loginType으로 사용
        return jwtTokenProvider.generateTokenPair(member.getId(), member.getRole(), "senior");
    }
}
