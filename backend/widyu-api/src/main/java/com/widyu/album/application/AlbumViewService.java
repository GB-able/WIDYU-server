package com.widyu.album.application;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import com.widyu.album.Album;
import com.widyu.album.AlbumView;
import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.member.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumViewService {

    private final AlbumViewRepository albumViewRepository;

    @Transactional(propagation = REQUIRES_NEW)
    public void recordView(Album album, Member member) {
        Optional<AlbumView> existingView = albumViewRepository.findByAlbumAndMember(album, member);
        
        if (existingView.isEmpty()) {
            AlbumView albumView = AlbumView.createView(album, member);
            albumViewRepository.save(albumView);
            
            album.incrementViewCount();
            
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