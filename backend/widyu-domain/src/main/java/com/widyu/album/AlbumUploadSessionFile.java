package com.widyu.album;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
public class AlbumUploadSessionFile {

    private final int index;
    private final MediaType mediaType;
    private final String fileName;
    private final String contentType;
    private final long fileSize;
    private final String objectKey;
    private final String uploadId;
    private final int partCount;

    @Builder(access = AccessLevel.PRIVATE)
    private AlbumUploadSessionFile(final int index, final MediaType mediaType, final String fileName,
                                   final String contentType, final long fileSize, final String objectKey,
                                   final String uploadId, final int partCount) {
        this.index = index;
        this.mediaType = mediaType;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.objectKey = objectKey;
        this.uploadId = uploadId;
        this.partCount = partCount;
    }

    public static AlbumUploadSessionFile photo(final int index, final String fileName, final String contentType,
                                               final long fileSize, final String objectKey) {
        return AlbumUploadSessionFile.builder()
                .index(index)
                .mediaType(MediaType.PHOTO)
                .fileName(fileName)
                .contentType(contentType)
                .fileSize(fileSize)
                .objectKey(objectKey)
                .build();
    }

    public static AlbumUploadSessionFile video(final int index, final String fileName, final String contentType,
                                               final long fileSize, final String objectKey,
                                               final String uploadId, final int partCount) {
        return AlbumUploadSessionFile.builder()
                .index(index)
                .mediaType(MediaType.VIDEO)
                .fileName(fileName)
                .contentType(contentType)
                .fileSize(fileSize)
                .objectKey(objectKey)
                .uploadId(uploadId)
                .partCount(partCount)
                .build();
    }

    public boolean isVideo() {
        return mediaType == MediaType.VIDEO;
    }
}
