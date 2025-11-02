package com.widyu.goal.home.application;

import com.widyu.global.util.MemberUtil;
import com.widyu.goal.home.dto.response.FamilyListResponse;
import com.widyu.goal.home.dto.response.FamilyMemberResponse;
import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.repository.FamilyConnectionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalHomeService {

    private final FamilyConnectionRepository familyConnectionRepository;
    private final MemberUtil memberUtil;

    public FamilyListResponse getFamilyList() {
        Member currentMember = memberUtil.getCurrentMember();

        List<FamilyConnection> familyConnections =
                familyConnectionRepository.findAllByGuardianIdWithSeniorAndMember(currentMember.getId());

        List<FamilyMemberResponse> families = familyConnections.stream()
                .map(FamilyMemberResponse::from)
                .toList();

        return FamilyListResponse.of(families);
    }
}
