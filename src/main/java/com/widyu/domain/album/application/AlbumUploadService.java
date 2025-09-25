package com.widyu.domain.album.application;

import com.widyu.domain.album.dto.request.AlbumUploadRequest;
import com.widyu.domain.album.dto.response.AlbumUploadResponse;
import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.repository.AlbumRepository;
import com.widyu.domain.member.entity.Member;
import com.widyu.global.util.MemberUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumUploadService {

    private final AlbumRepository albumRepository;
    private final AlbumFileService albumFileService;
    private final MemberUtil memberUtil;
    private final AlbumMediaPolicy mediaPolicy;

    @Transactional
    public AlbumUploadResponse uploadAlbum(AlbumUploadRequest request) {
        // 1) 사용자
        Member currentMember = memberUtil.getCurrentMember();

        // 2) 정책 검증(개수/타입/용량)
        mediaPolicy.validate(request.mediaFiles());

        // 3) 업로드 (이미지/비디오+썸네일/길이)
        AlbumFileService.UploadResult uploadResult =
                albumFileService.uploadMediaFilesWithThumbnails(request.mediaFiles(), currentMember.getId());

        // 4) 앨범 저장
        Album album = Album.createAlbumWithMetadata(
                currentMember,
                request.content(),
                uploadResult.mediaUrls(),
                uploadResult.thumbnailUrls(),
                uploadResult.durations()
        );
        Album saved = albumRepository.save(album);

        return AlbumUploadResponse.from(saved);
    }
}
