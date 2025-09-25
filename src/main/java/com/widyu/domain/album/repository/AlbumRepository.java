package com.widyu.domain.album.repository;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.member.entity.Member;
import com.widyu.global.domain.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    /**
     * 활성 상태인 앨범만 조회 (삭제된 앨범 제외)
     */
    @Query("SELECT a FROM Album a WHERE a.status = :status ORDER BY a.createdAt DESC")
    Page<Album> findByStatusOrderByCreatedAtDesc(@Param("status") Status status, Pageable pageable);

    /**
     * 특정 회원의 활성 앨범 조회
     */
    @Query("SELECT a FROM Album a WHERE a.member = :member AND a.status = :status ORDER BY a.createdAt DESC")
    Page<Album> findByMemberAndStatusOrderByCreatedAtDesc(@Param("member") Member member, @Param("status") Status status, Pageable pageable);

    /**
     * 특정 기간의 활성 앨범 조회
     */
    @Query("SELECT a FROM Album a WHERE a.status = :status AND a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    List<Album> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("status") Status status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 활성 상태인 앨범을 ID로 조회
     */
    @Query("SELECT a FROM Album a WHERE a.id = :id AND a.status = :status")
    Optional<Album> findByIdAndStatus(@Param("id") Long id, @Param("status") Status status);

    /**
     * 특정 회원의 활성 앨범 개수 조회
     */
    @Query("SELECT COUNT(a) FROM Album a WHERE a.member = :member AND a.status = :status")
    long countByMemberAndStatus(@Param("member") Member member, @Param("status") Status status);


    /**
     * 최신 앨범 ID 조회 (첫 페이지용) - createdAt, id 복합 정렬
     */
    @Query("SELECT a.id FROM Album a WHERE a.status = 'ACTIVE' ORDER BY a.createdAt DESC, a.id DESC")
    org.springframework.data.domain.Slice<Long> findLatestAlbumIds(Pageable pageable);

    /**
     * 앨범 ID들로 컬렉션과 함께 조회
     */
    @Query("SELECT DISTINCT a FROM Album a LEFT JOIN FETCH a.mediaUrls LEFT JOIN FETCH a.thumbnailUrls LEFT JOIN FETCH a.durations WHERE a.id IN :albumIds ORDER BY a.createdAt DESC, a.id DESC")
    List<Album> findAlbumsWithCollectionsByIds(@Param("albumIds") List<Long> albumIds);

    /**
     * 특정 postId 이후의 앨범 ID 조회 (무한 스크롤용)
     */
    @Query("SELECT a.id FROM Album a WHERE a.status = 'ACTIVE' AND a.id < :lastPostId ORDER BY a.id DESC")
    org.springframework.data.domain.Slice<Long> findAlbumIdsAfterPostId(
            @Param("lastPostId") Long lastPostId,
            Pageable pageable
    );

    /**
     * 모든 활성 앨범 조회 (생성일 순)
     */
    @Query("SELECT a FROM Album a WHERE a.status = 'ACTIVE' ORDER BY a.createdAt DESC")
    List<Album> findAllActiveAlbumsOrderByCreatedAtDesc();

}