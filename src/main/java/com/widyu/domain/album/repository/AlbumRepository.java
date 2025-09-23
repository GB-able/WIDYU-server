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
     * 특정 회원의 마지막 앨범 업로드 시간 조회
     */
    @Query("SELECT a.createdAt FROM Album a WHERE a.member = :member AND a.status = :status ORDER BY a.createdAt DESC LIMIT 1")
    Optional<LocalDateTime> findLastUploadDateByMember(@Param("member") Member member, @Param("status") Status status);
}