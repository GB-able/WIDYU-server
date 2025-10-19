package com.widyu.album.repository;

import com.widyu.album.Album;
import com.widyu.member.Member;
import com.widyu.global.entity.Status;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    @Query("SELECT a FROM Album a WHERE a.id = :id AND a.status = :status")
    Optional<Album> findByIdAndStatus(@Param("id") Long id, @Param("status") Status status);

    @Query("SELECT DISTINCT a FROM Album a LEFT JOIN FETCH a.mediaUrls LEFT JOIN FETCH a.thumbnailUrls LEFT JOIN FETCH a.durations WHERE a.id = :id AND a.status = :status")
    Optional<Album> findByIdAndStatusWithCollections(@Param("id") Long id, @Param("status") Status status);

    @Query("SELECT a.id FROM Album a WHERE a.status = 'ACTIVE' ORDER BY a.createdAt DESC, a.id DESC")
    org.springframework.data.domain.Slice<Long> findLatestAlbumIds(Pageable pageable);

    @Query("SELECT DISTINCT a FROM Album a LEFT JOIN FETCH a.mediaUrls LEFT JOIN FETCH a.thumbnailUrls LEFT JOIN FETCH a.durations WHERE a.id IN :albumIds ORDER BY a.createdAt DESC, a.id DESC")
    List<Album> findAlbumsWithCollectionsByIds(@Param("albumIds") List<Long> albumIds);

    @Query("SELECT a.id FROM Album a WHERE a.status = 'ACTIVE' AND a.id < :lastPostId ORDER BY a.id DESC")
    org.springframework.data.domain.Slice<Long> findAlbumIdsAfterPostId(
            @Param("lastPostId") Long lastPostId,
            Pageable pageable
    );

    @Query("SELECT a.createdAt FROM Album a WHERE a.member = :member AND a.status = :status ORDER BY a.createdAt DESC LIMIT 1")
    Optional<LocalDateTime> findLastUploadDateByMember(@Param("member") Member member, @Param("status") Status status);

    long countByMemberId(Long id);
    
    @Query("SELECT a.id FROM Album a WHERE a.status = 'ACTIVE' AND DATE(a.createdAt) = :date ORDER BY a.createdAt DESC, a.id DESC")
    org.springframework.data.domain.Slice<Long> findLatestAlbumIdsByDate(@Param("date") LocalDate date, Pageable pageable);
    
    @Query("SELECT a.id FROM Album a WHERE a.status = 'ACTIVE' AND DATE(a.createdAt) = :date AND a.id < :lastPostId ORDER BY a.id DESC")
    org.springframework.data.domain.Slice<Long> findAlbumIdsAfterPostIdByDate(
            @Param("lastPostId") Long lastPostId,
            @Param("date") LocalDate date,
            Pageable pageable
    );
}
