package com.widyu.domain.album.repository;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.entity.AlbumLike;
import com.widyu.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlbumLikeRepository extends JpaRepository<AlbumLike, Long> {
    
    boolean existsByAlbumAndMember(Album album, Member member);
    
    Optional<AlbumLike> findByAlbumAndMember(Album album, Member member);
    
    @Query("SELECT al.album.id FROM AlbumLike al WHERE al.member = :member AND al.album.id IN :albumIds")
    List<Long> findLikedAlbumIds(@Param("member") Member member, @Param("albumIds") List<Long> albumIds);
    
    @Query("SELECT al.album.id FROM AlbumLike al WHERE al.member = :member")
    List<Long> findAlbumIdsByMember(@Param("member") Member member);
}
