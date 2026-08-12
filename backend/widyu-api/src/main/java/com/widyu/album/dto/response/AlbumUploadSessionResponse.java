package com.widyu.album.dto.response;

import com.widyu.album.MediaType;

import java.util.List;

public record AlbumUploadSessionResponse(
        String sessionId,
        long expiresInSeconds,
        List<FileUploadTarget> files
) {
    public static AlbumUploadSessionResponse of(String sessionId, long expiresInSeconds,
                                                List<FileUploadTarget> files) {
        return new AlbumUploadSessionResponse(sessionId, expiresInSeconds, files);
    }

    public record FileUploadTarget(
            int index,
            MediaType mediaType,
            String objectKey,
            String uploadUrl,
            Long partSizeBytes,
            List<PartUploadUrl> parts
    ) {
        public static FileUploadTarget photo(int index, String objectKey, String uploadUrl) {
            return new FileUploadTarget(index, MediaType.PHOTO, objectKey, uploadUrl, null, null);
        }

        public static FileUploadTarget video(int index, String objectKey, long partSizeBytes,
                                             List<PartUploadUrl> parts) {
            return new FileUploadTarget(index, MediaType.VIDEO, objectKey, null, partSizeBytes, parts);
        }
    }

    public record PartUploadUrl(int partNumber, String uploadUrl) {

        public static PartUploadUrl of(int partNumber, String uploadUrl) {
            return new PartUploadUrl(partNumber, uploadUrl);
        }
    }
}
