package com.widyu.domain.album.repository;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.member.entity.Member;
import com.widyu.global.domain.Status;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlbumCalendarRepository extends JpaRepository<Album, Long> {
    List<Album> findAllByMemberAndCreatedAtBetweenAndStatus(
            Member member, LocalDateTime start, LocalDateTime end, Status status
    );

    @Query("""
        SELECT DISTINCT a FROM Album a
        WHERE a.status = :status
        AND a.createdAt BETWEEN :start AND :end
        AND (:cursor IS NULL OR a.id < :cursor)
        AND (
            a.member.id = :memberId
            OR a.member.id IN (
                SELECT pp1.member.id FROM ParentProfile pp1 
                WHERE pp1.guardian.id = :memberId
            )
            OR a.member.id IN (
                SELECT pp2.guardian.id FROM ParentProfile pp2
                WHERE pp2.member.id = :memberId
            )
            OR a.member.id IN (
                SELECT pp3.guardian.id FROM ParentProfile pp3
                WHERE pp3.member.id IN (
                    SELECT pp4.member.id FROM ParentProfile pp4
                    WHERE pp4.guardian.id = :memberId
                )
            )
        )
        ORDER BY a.id DESC
        LIMIT :limit
        """)
    List<Album> findFamilyAlbumsByDateRangeWithCursor(Long memberId, LocalDateTime start, LocalDateTime end, 
                                                     Long cursor, int limit, Status status);
}