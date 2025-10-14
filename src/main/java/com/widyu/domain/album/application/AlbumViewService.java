package com.widyu.domain.album.application;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.entity.AlbumView;
import com.widyu.domain.album.repository.AlbumViewRepository;
import com.widyu.domain.fcm.event.album.dto.AlbumViewedEvent;
import com.widyu.domain.member.entity.Member;
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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = REQUIRES_NEW)
    public void recordView(Album album, Member member) {
        Optional<AlbumView> existingView = albumViewRepository.findByAlbumAndMember(album, member);
        
        if (existingView.isEmpty()) {
            AlbumView albumView = AlbumView.createView(album, member);
            albumViewRepository.save(albumView);
            
            album.incrementViewCount();


            // 앨범 조회 알림 이벤트 발행
            eventPublisher.publishEvent(new AlbumViewedEvent(
                    member.getId(),
                    album.getId()
            ));

            log.info("앨범 첫 조회 기록: albumId={}, memberId={}", album.getId(), member.getId());
        }
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