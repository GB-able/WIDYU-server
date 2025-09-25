package com.widyu.domain.album.repository;

import com.widyu.domain.album.entity.AlbumView;
import com.widyu.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlbumViewRepository extends JpaRepository<AlbumView, Long> {

    @Query("SELECT av.member FROM AlbumView av WHERE av.album.id = :albumId")
    List<Member> findMembersByAlbumId(@Param("albumId") Long albumId);

    @Query(value = """
        SELECT av.* FROM album_view av 
        WHERE av.album_id = :albumId 
        ORDER BY av.created_at DESC 
        LIMIT 3
    """, nativeQuery = true)
    List<AlbumView> findTop3ViewersByAlbum(@Param("albumId") Long albumId);

    @Query(value = """
        SELECT av.* FROM album_view av
        WHERE av.album_id IN :albumIds
        AND (
            SELECT COUNT(*) FROM album_view av2 
            WHERE av2.album_id = av.album_id 
            AND av2.created_at > av.created_at
        ) < 3
        ORDER BY av.album_id, av.created_at DESC
    """, nativeQuery = true)
    List<AlbumView> findTop3ViewersForAlbums(@Param("albumIds") List<Long> albumIds);
}