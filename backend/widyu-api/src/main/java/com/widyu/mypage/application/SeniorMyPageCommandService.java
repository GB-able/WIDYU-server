package com.widyu.mypage.application;

import com.widyu.mypage.dto.request.UpdateNameRequest;
import com.widyu.mypage.dto.request.UpdatePhoneRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SeniorMyPageCommandService {

    private final SeniorMyPageService seniorMyPageService;

    public void updateName(UpdateNameRequest request) { seniorMyPageService.updateName(request); }
    public void updateProfileImage(MultipartFile image) { seniorMyPageService.updateProfileImage(image); }
    public void updatePhoneNumber(UpdatePhoneRequest request) { seniorMyPageService.updatePhoneNumber(request); }
    public void updateRepresentativeContact(Long memberId) { seniorMyPageService.updateRepresentativeContact(memberId); }
}
