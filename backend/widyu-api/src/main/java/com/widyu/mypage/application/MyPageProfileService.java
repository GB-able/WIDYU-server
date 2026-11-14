package com.widyu.mypage.application;

import com.widyu.album.application.AlbumMediaPolicy;
import com.widyu.album.application.AlbumMediaPolicy.ImageFormat;
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
        // 형식과 확장자는 클라이언트 값이 아닌 검증한 형식으로 정한다(LLD-0150).
        ImageFormat format = AlbumMediaPolicy.validateImageFile(image);
        String oldImageUrl = member.getProfileImage();
        String filePath = s3Service.generateFilePath("profile", "profile." + format.extension());
        String newImageUrl = s3Service.uploadFile(image, filePath, format.contentType());

        member.updateProfileImage(newImageUrl);
        deleteProfileImage(s3Service, oldImageUrl);
    }

    public static void deleteProfileImage(S3Service s3Service, String imageUrl) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            s3Service.deleteFile(imageUrl);
        }
    }
}
