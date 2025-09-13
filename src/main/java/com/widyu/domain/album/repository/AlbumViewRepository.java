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
}