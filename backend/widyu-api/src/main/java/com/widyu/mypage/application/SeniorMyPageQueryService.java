package com.widyu.mypage.application;

import com.widyu.mypage.dto.response.EmergencyContactResponse;
import com.widyu.mypage.dto.response.FamilyCodeResponse;
import com.widyu.mypage.dto.response.PointHistoryResponse;
import com.widyu.mypage.dto.response.SeniorInfoResponse;
import com.widyu.mypage.dto.response.SeniorProfileDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeniorMyPageQueryService {

    private final SeniorMyPageService seniorMyPageService;

    public SeniorInfoResponse getSeniorInfo() { return seniorMyPageService.getSeniorInfo(); }
    public FamilyCodeResponse getFamilyCode() { return seniorMyPageService.getFamilyCode(); }
    public SeniorProfileDetailResponse getProfileDetail() { return seniorMyPageService.getProfileDetail(); }
    public PointHistoryResponse getPointHistory() { return seniorMyPageService.getPointHistory(); }
    public EmergencyContactResponse getEmergencyContacts() { return seniorMyPageService.getEmergencyContacts(); }
}
