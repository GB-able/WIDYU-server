package com.widyu.mypage.application;

import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.mypage.dto.request.UpdateNameRequest;
import org.springframework.web.multipart.MultipartFile;

public final class MyPageProfileService {

    private MyPageProfileService() {
    }

    public static Member getCurrentMember(MemberUtil memberUtil) {
        return memberUtil.getCurrentMember();
    }

    public static void updateCurrentMemberName(MemberUtil memberUtil, UpdateNameRequest request) {
        Member member = getCurrentMember(memberUtil);
        member.updateName(request.name());
    }

    public static void updateCurrentMemberProfileImage(
            MemberUtil memberUtil,
            S3Service s3Service,
            MultipartFile image
    ) {
        Member member = getCurrentMember(memberUtil);
        updateMemberProfileImage(s3Service, member, image);
    }

    public static void updateMemberProfileImage(S3Service s3Service, Member member, MultipartFile image) {
        String oldImageUrl = member.getProfileImage();
        String filePath = generateProfileImagePath(s3Service, image);
        String newImageUrl = uploadProfileImage(s3Service, image, filePath);

        member.updateProfileImage(newImageUrl);
        deleteProfileImage(s3Service, oldImageUrl);
    }

    public static String generateProfileImagePath(S3Service s3Service, MultipartFile image) {
        return s3Service.generateFilePath("profile", image.getOriginalFilename());
    }

    public static String uploadProfileImage(S3Service s3Service, MultipartFile image, String filePath) {
        return s3Service.uploadFile(image, filePath);
    }

    public static void deleteProfileImage(S3Service s3Service, String imageUrl) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            s3Service.deleteFile(imageUrl);
        }
    }
}
