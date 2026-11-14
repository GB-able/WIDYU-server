package com.widyu.album.application;

import com.widyu.album.Album;
import com.widyu.album.AlbumView;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.fcm.event.album.dto.AlbumViewedEvent;
import com.widyu.member.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumViewService {

    private final AlbumViewRepository albumViewRepository;
    private final AlbumRepository albumRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 처음 보는 앨범이면 조회 기록을 남기고 조회수를 올린다. 새로 남겼으면 true다.
     *
     * <p>상세 읽기 트랜잭션이 끝난 뒤 파사드가 부른다. 읽기 안에서 부르면 요청 하나가 커넥션 두 개를 쥔다(LLD-0152).
     * 조회수를 기록보다 먼저 올려 앨범 행을 잠근다. 기록을 먼저 넣으면 같은 회원의 첫 조회 두 건이 외래키 공유 잠금과
     * 조회수 배타 잠금을 서로 기다려 교착된다. 이 순서에서는 뒤 요청이 앞 요청의 커밋을 기다린 뒤 유일 키 위반으로 끝난다.
     */
    @Transactional
    public boolean recordView(Long albumId, Member member) {
        Album album = albumRepository.getReferenceById(albumId);
        Optional<AlbumView> existingView = albumViewRepository.findByAlbumAndMember(album, member);
        if (existingView.isPresent()) {
            return false;
        }

        albumRepository.incrementViewCount(albumId);
        albumViewRepository.save(AlbumView.createView(album, member));

        // 앨범 조회 알림 이벤트 발행
        eventPublisher.publishEvent(new AlbumViewedEvent(member.getId(), albumId));

        log.info("앨범 첫 조회 기록: albumId={}, memberId={}", albumId, member.getId());
        return true;
    }

    @Transactional(readOnly = true)
    public List<Member> getRecentViewers(Album album, int limit) {
        return albumViewRepository.findViewersByAlbum(album, limit);
    }

    @Transactional(readOnly = true)
    public Long getTotalViewCount(Album album) {
        return albumViewRepository.countViewsByAlbum(album);
    }
}
