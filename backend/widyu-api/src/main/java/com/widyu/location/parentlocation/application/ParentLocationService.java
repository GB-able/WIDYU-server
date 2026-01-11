package com.widyu.location.parentlocation.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.location.parentlocation.dto.request.ParentLocationCreateRequest;
import com.widyu.location.parentlocation.dto.response.LocationInfo;
import com.widyu.location.parentlocation.dto.response.ParentLocationResponse;
import com.widyu.location.parentlocation.dto.response.SeniorWithLocationsResponse;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.MemberRepository;
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
    private final FamilyConnectionRepository familyConnectionRepository;
    private final MemberUtil memberUtil;

    public List<SeniorWithLocationsResponse> findAllByGuardianId() {
        Long guardianId = memberUtil.getCurrentMember().getId();

        List<FamilyConnection> familyConnections = familyConnectionRepository
                .findAllByGuardianIdWithSeniorAndMember(guardianId);

        return familyConnections.stream()
                .map(fc -> {
                    Member senior = fc.getSenior().getMember();
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
        Member seniorMember = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 회원입니다."));

        if (parentLocationRepository.existsByMemberAndPlaceAddress(seniorMember, request.placeAddress())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "이미 등록된 주소입니다.");
        }

        parentLocationRepository.save(request.toEntity(seniorMember));
    }

    @Transactional
    public void delete(Long memberId, Long parentLocationId) {
        Member seniorMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 회원입니다."));

        ParentLocation location = parentLocationRepository.findByIdAndMember(parentLocationId, seniorMember)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "이미 삭제된 장소입니다."));

        parentLocationRepository.delete(location);
    }
}
