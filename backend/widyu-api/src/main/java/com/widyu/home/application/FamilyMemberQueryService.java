package com.widyu.home.application;

import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FamilyMemberQueryService {

    private final SeniorProfileRepository seniorProfileRepository;
    private final FamilyMembershipRepository familyMembershipRepository;

    public List<Long> getFamilyMemberIds(Member senior) {
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(senior.getId())
                .orElse(null);

        if (seniorProfile == null) {
            return List.of(senior.getId());
        }

        Long familyId = seniorProfile.getFamily().getId();

        List<Long> seniorIds = seniorProfileRepository.findAllByFamilyId(familyId).stream()
                .map(sp -> sp.getMember().getId())
                .toList();

        List<Long> guardianIds = familyMembershipRepository.findAllByFamilyIdWithGuardian(familyId).stream()
                .map(fm -> fm.getGuardian().getId())
                .toList();

        return java.util.stream.Stream.concat(seniorIds.stream(), guardianIds.stream()).toList();
    }
}
