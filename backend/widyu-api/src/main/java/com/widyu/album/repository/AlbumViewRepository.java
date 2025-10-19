package com.widyu.album.repository;

import com.widyu.album.Album;
import com.widyu.album.AlbumView;
import com.widyu.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlbumViewRepository extends JpaRepository<AlbumView, Long> {
    
    Optional<AlbumView> findByAlbumAndMember(Album album, Member member);

    @Query("SELECT av.member FROM AlbumView av WHERE av.album.id = :albumId")
    List<Member> findMembersByAlbumId(@Param("albumId") Long albumId);
    
    @Query("SELECT av.member FROM AlbumView av WHERE av.album = :album ORDER BY av.createdAt DESC LIMIT :limit")
    List<Member> findViewersByAlbum(@Param("album") Album album, @Param("limit") int limit);
    
    @Query("SELECT COUNT(av) FROM AlbumView av WHERE av.album = :album")
    Long countViewsByAlbum(@Param("album") Album album);

    @Query(value = """
        SELECT av.* FROM album_view av
        WHERE av.album_id IN :albumIds
        AND (
            SELECT COUNT(*) FROM album_view av2\s
            WHERE av2.album_id = av.album_id\s
            AND av2.created_at > av.created_at
        ) < 3
        ORDER BY av.album_id, av.created_at DESC
   \s""", nativeQuery = true)
    List<AlbumView> findTop3ViewersForAlbums(@Param("albumIds") List<Long> albumIds);

    @Query("SELECT COUNT(DISTINCT a.id) FROM Album a WHERE a.member.id = :parentMemberId AND a.status = com.widyu.global.entity.Status.ACTIVE")
    long countTotalAlbumsByParent(@Param("parentMemberId") Long parentMemberId);

    @Query("SELECT COUNT(DISTINCT av.album.id) FROM AlbumView av WHERE av.member.id = :guardianId AND av.album.member.id = :parentMemberId AND av.album.status = com.widyu.global.entity.Status.ACTIVE")
    long countViewedAlbumsByGuardianAndParent(@Param("guardianId") Long guardianId, @Param("parentMemberId") Long parentMemberId);

    long countViewedAlbumsByMemberIdAndAlbumId(Long viewerId, Long albumId);
}