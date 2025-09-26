package com.widyu.domain.album.repository;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.entity.AlbumUnlock;
import com.widyu.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlbumUnlockRepository extends JpaRepository<AlbumUnlock, Long> {
    boolean existsByAlbumAndMember(Album album, Member member);
    
    @Query("SELECT au.album.id FROM AlbumUnlock au WHERE au.member = :member ORDER BY au.unlockedAt DESC")
    List<Long> findUnlockedAlbumIdsByMember(@Param("member") Member member);
    
    @Query("SELECT COUNT(au) * 50 FROM AlbumUnlock au WHERE au.member = :member")
    Long getTotalUnlockPriceByMember(@Param("member") Member member);
}