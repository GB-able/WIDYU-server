package com.widyu.domain.album.repository;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.member.entity.Member;
import com.widyu.global.domain.Status;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlbumCalendarRepository extends JpaRepository<Album, Long> {
    List<Album> findAllByMemberAndCreatedAtBetweenAndStatus(
            Member member, LocalDateTime start, LocalDateTime end, Status status
    );
}