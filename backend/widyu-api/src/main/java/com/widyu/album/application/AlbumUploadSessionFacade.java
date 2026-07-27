package com.widyu.album.application;

import com.widyu.album.dto.request.AlbumUploadCompleteRequest;
import com.widyu.album.dto.request.AlbumUploadSessionCreateRequest;
import com.widyu.album.dto.response.AlbumUploadAcceptedResponse;
import com.widyu.album.dto.response.AlbumUploadSessionResponse;

public interface AlbumUploadSessionFacade {

    AlbumUploadSessionResponse createUploadSession(AlbumUploadSessionCreateRequest request);

    AlbumUploadAcceptedResponse completeUpload(String sessionId, AlbumUploadCompleteRequest request);
}
