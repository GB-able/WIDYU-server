package com.widyu.domain.album.application;

import com.widyu.domain.album.dto.request.AlbumUploadRequest;
import com.widyu.domain.album.dto.response.AlbumUploadResponse;
import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.repository.AlbumRepository;
import com.widyu.domain.member.entity.Member;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AlbumUploadService {

    private final AlbumRepository albumRepository;
    private final AlbumFileService albumFileService;
    private final MemberUtil memberUtil;

    @Transactional
    public AlbumUploadResponse uploadAlbum(AlbumUploadRequest request) {
        // 1. 현재 사용자 조회
        Member currentMember = memberUtil.getCurrentMember();

        // 2. 미디어 파일 유효성 검증
        validateMediaFiles(request);

        // 3. S3에 파일 업로드
        List<String> mediaUrls = albumFileService.uploadMediaFiles(request.mediaFiles(), currentMember.getId());

        // 4. 앨범 엔티티 생성 및 저장
        Album album = Album.createAlbum(currentMember, request.content(), mediaUrls);
        Album savedAlbum = albumRepository.save(album);

        return AlbumUploadResponse.from(savedAlbum);
    }

    private void validateMediaFiles(AlbumUploadRequest request) {
        if (request.mediaFiles() == null || request.mediaFiles().isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_IS_EMPTY);
        }

        if (!request.hasValidMediaCount()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "전체 최대 8개, 사진 최대 8개, 동영상 최대 3개까지 업로드 가능합니다.");
        }

        for (MultipartFile file : request.mediaFiles()) {
            if (file.isEmpty()) {
                throw new BusinessException(ErrorCode.FILE_IS_EMPTY);
            }

            String contentType = file.getContentType();
            if (contentType == null) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
            }

            if (!isValidMediaType(contentType)) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
            }
        }
    }

    private boolean isValidMediaType(String contentType) {
        return contentType.startsWith("image/") || contentType.startsWith("video/");
    }
}