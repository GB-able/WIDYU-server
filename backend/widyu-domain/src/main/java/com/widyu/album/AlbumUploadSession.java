package com.widyu.album;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@Getter
@EqualsAndHashCode(of = "id")
@RedisHash(value = "albumUploadSession")
public class AlbumUploadSession {

    public static final long WAITING_TTL_SECONDS = 21600;
    public static final long COMPLETED_TTL_SECONDS = 600;

    @Id
    private String id;

    private Long memberId;
    private AlbumUploadSessionStatus status;
    private Long albumId;
    private List<AlbumUploadSessionFile> files;

    @TimeToLive
    private final long ttl;

    @Builder(access = AccessLevel.PRIVATE)
    private AlbumUploadSession(final String id, final Long memberId, final AlbumUploadSessionStatus status,
                               final Long albumId, final List<AlbumUploadSessionFile> files, final long ttl) {
        this.id = id;
        this.memberId = memberId;
        this.status = status;
        this.albumId = albumId;
        this.files = files;
        this.ttl = ttl;
    }

    public static AlbumUploadSession createWaiting(final String id, final Long memberId,
                                                   final List<AlbumUploadSessionFile> files) {
        return AlbumUploadSession.builder()
                .id(id)
                .memberId(memberId)
                .status(AlbumUploadSessionStatus.WAITING)
                .files(files)
                .ttl(WAITING_TTL_SECONDS)
                .build();
    }

    public AlbumUploadSession complete(final Long albumId) {
        return AlbumUploadSession.builder()
                .id(id)
                .memberId(memberId)
                .status(AlbumUploadSessionStatus.COMPLETED)
                .albumId(albumId)
                .files(files)
                .ttl(COMPLETED_TTL_SECONDS)
                .build();
    }

    public boolean isCompleted() {
        return status == AlbumUploadSessionStatus.COMPLETED;
    }

    public boolean isOwnedBy(final Long candidateMemberId) {
        return memberId != null && memberId.equals(candidateMemberId);
    }

    public List<AlbumUploadSessionFile> getVideoFiles() {
        return files.stream()
                .filter(AlbumUploadSessionFile::isVideo)
                .toList();
    }
}
