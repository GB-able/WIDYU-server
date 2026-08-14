package com.widyu.album.repository;

import com.widyu.album.Album;
import com.widyu.global.entity.Status;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlbumCalendarRepository extends JpaRepository<Album, Long> {
    List<Album> findAllByMemberIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatus(
            List<Long> memberIds, LocalDateTime start, LocalDateTime end, Status status
    );

    @Query("""
        SELECT DISTINCT a FROM Album a
        WHERE a.status = :status
        AND a.createdAt BETWEEN :start AND :end
        AND (:cursor IS NULL OR a.id < :cursor)
        AND (
            a.member.id = :memberId
            OR a.member.id IN (
                SELECT sp1.member.id FROM SeniorProfile sp1
                WHERE sp1.family.id IN (
                    SELECT fm1.family.id FROM FamilyMembership fm1 WHERE fm1.guardian.id = :memberId
                )
            )
            OR a.member.id IN (
                SELECT fm2.guardian.id FROM FamilyMembership fm2
                WHERE fm2.family.id IN (
                    SELECT sp2.family.id FROM SeniorProfile sp2 WHERE sp2.member.id = :memberId
                )
            )
            OR a.member.id IN (
                SELECT fm3.guardian.id FROM FamilyMembership fm3
                WHERE fm3.family.id IN (
                    SELECT fm4.family.id FROM FamilyMembership fm4 WHERE fm4.guardian.id = :memberId
                )
                AND fm3.guardian.id <> :memberId
            )
        )
        ORDER BY a.id DESC
        LIMIT :limit
        """)
    List<Album> findFamilyAlbumsByDateRangeWithCursor(Long memberId, LocalDateTime start, LocalDateTime end,
                                                     Long cursor, int limit, Status status);
}
