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

    @Query("SELECT COUNT(DISTINCT a.id) FROM Album a WHERE a.member.id = :parentMemberId AND a.status = com.widyu.global.domain.Status.ACTIVE")
    long countTotalAlbumsByParent(@Param("parentMemberId") Long parentMemberId);

    @Query("SELECT COUNT(DISTINCT av.album.id) FROM AlbumView av WHERE av.member.id = :guardianId AND av.album.member.id = :parentMemberId AND av.album.status = com.widyu.global.domain.Status.ACTIVE")
    long countViewedAlbumsByGuardianAndParent(@Param("guardianId") Long guardianId, @Param("parentMemberId") Long parentMemberId);

    long countViewedAlbumsByMemberIdAndAlbumId(Long viewerId, Long albumId);
}