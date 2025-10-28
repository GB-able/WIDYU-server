package com.widyu.album.repository;

import com.widyu.album.Album;
import com.widyu.member.Member;
import com.widyu.global.entity.Status;
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
                SELECT fc1.senior.member.id FROM FamilyConnection fc1
                WHERE fc1.guardian.id = :memberId
            )
            OR a.member.id IN (
                SELECT fc2.guardian.id FROM FamilyConnection fc2
                WHERE fc2.senior.member.id = :memberId
            )
            OR a.member.id IN (
                SELECT fc3.guardian.id FROM FamilyConnection fc3
                WHERE fc3.senior.id IN (
                    SELECT fc4.senior.id FROM FamilyConnection fc4
                    WHERE fc4.guardian.id = :memberId
                )
            )
        )
        ORDER BY a.id DESC
        LIMIT :limit
        """)
    List<Album> findFamilyAlbumsByDateRangeWithCursor(Long memberId, LocalDateTime start, LocalDateTime end, 
                                                     Long cursor, int limit, Status status);
}
