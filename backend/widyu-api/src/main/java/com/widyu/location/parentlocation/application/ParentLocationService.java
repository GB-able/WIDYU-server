package com.widyu.location.parentlocation.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.location.parentlocation.dto.request.ParentLocationCreateRequest;
import com.widyu.location.parentlocation.dto.request.ParentLocationUpdateRequest;
import com.widyu.location.parentlocation.dto.response.LocationInfo;
import com.widyu.location.parentlocation.dto.response.ParentLocationResponse;
import com.widyu.location.parentlocation.dto.response.SeniorWithLocationsResponse;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.application.FamilyAccessService;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.parentlocation.LocationType;
import com.widyu.parentlocation.ParentLocation;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentLocationService {

    private final ParentLocationRepository parentLocationRepository;
    private final MemberRepository memberRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final MemberUtil memberUtil;
    private final FamilyAccessService familyAccessService;

    public List<SeniorWithLocationsResponse> findAllByGuardianId() {
        Long guardianId = memberUtil.getCurrentMember().getId();

        FamilyMembership myMembership = familyMembershipRepository.findByGuardianId(guardianId)
                .orElse(null);

        if (myMembership == null) {
            return List.of();
        }

        List<SeniorProfile> seniors = seniorProfileRepository
                .findAllByFamilyIdWithMember(myMembership.getFamily().getId());

        return seniors.stream()
                .map(sp -> {
                    Member senior = sp.getMember();
                    List<LocationInfo> locations = parentLocationRepository.findAllByMember(senior)
                            .stream()
                            .map(LocationInfo::of)
                            .collect(Collectors.toList());
                    return SeniorWithLocationsResponse.of(senior, locations);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void create(ParentLocationCreateRequest request) {
        if (request.locationType() == LocationType.HOME) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "집 주소는 마이페이지에서만 수정할 수 있습니다.");
        }

        Long guardianId = memberUtil.getCurrentMember().getId();
        familyAccessService.verifyFamilyAccess(guardianId, request.memberId());

        Member seniorMember = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 회원입니다."));

        if (parentLocationRepository.existsByMemberAndPlaceAddress(seniorMember, request.placeAddress())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "이미 등록된 주소입니다.");
        }

        parentLocationRepository.save(request.toEntity(seniorMember));
    }

    @Transactional
    public void delete(Long memberId, Long parentLocationId) {
        Long guardianId = memberUtil.getCurrentMember().getId();
        familyAccessService.verifyFamilyAccess(guardianId, memberId);

        Member seniorMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 회원입니다."));

        ParentLocation location = parentLocationRepository.findByIdAndMember(parentLocationId, seniorMember)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "이미 삭제된 장소입니다."));

        if (location.getLocationType() == LocationType.HOME) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "집 주소는 마이페이지에서만 수정할 수 있습니다.");
        }

        parentLocationRepository.delete(location);
    }

    @Transactional
    public void update(Long parentLocationId, ParentLocationUpdateRequest request) {
        if (request.locationType() == LocationType.HOME) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "집 주소는 마이페이지에서만 수정할 수 있습니다.");
        }

        Long guardianId = memberUtil.getCurrentMember().getId();
        familyAccessService.verifyFamilyAccess(guardianId, request.memberId());

        Member seniorMember = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 회원입니다."));

        ParentLocation location = parentLocationRepository.findByIdAndMember(parentLocationId, seniorMember)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 장소입니다."));

        if (location.getLocationType() == LocationType.HOME) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "집 주소는 마이페이지에서만 수정할 수 있습니다.");
        }

        location.update(
                request.locationType(),
                request.placeAddress(),
                request.latitude(),
                request.longitude(),
                request.name()
        );
    }
}
