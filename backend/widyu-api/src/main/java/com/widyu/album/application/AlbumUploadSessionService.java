package com.widyu.album.application;

import com.widyu.album.AlbumUploadSession;
import com.widyu.album.AlbumUploadSessionFile;
import com.widyu.album.repository.AlbumUploadSessionRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlbumUploadSessionService {

    private static final String COMPLETION_LOCK_PREFIX = "albumUploadSession:completing:";
    private static final long COMPLETION_LOCK_TTL_SECONDS = 600;

    private final AlbumUploadSessionRepository albumUploadSessionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public AlbumUploadSession saveWaitingSession(String sessionId, Long memberId,
                                                 List<AlbumUploadSessionFile> files) {
        return albumUploadSessionRepository.save(AlbumUploadSession.createWaiting(sessionId, memberId, files));
    }

    public AlbumUploadSession getSession(String sessionId) {
        return albumUploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_UPLOAD_SESSION_NOT_FOUND));
    }

    public void markCompleted(AlbumUploadSession session, Long albumId) {
        albumUploadSessionRepository.save(session.complete(albumId));
    }

    public void deleteSession(String sessionId) {
        albumUploadSessionRepository.deleteById(sessionId);
    }

    public boolean tryAcquireCompletionLock(String sessionId) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(COMPLETION_LOCK_PREFIX + sessionId, true, COMPLETION_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseCompletionLock(String sessionId) {
        redisTemplate.delete(COMPLETION_LOCK_PREFIX + sessionId);
    }
}
