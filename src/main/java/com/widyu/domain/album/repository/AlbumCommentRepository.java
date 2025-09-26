package com.widyu.domain.album.repository;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.entity.AlbumComment;
import com.widyu.global.domain.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlbumCommentRepository extends JpaRepository<AlbumComment, Long> {
    
    Optional<AlbumComment> findByIdAndStatus(Long id, Status status);
    
    @Query("SELECT c FROM AlbumComment c WHERE c.album = :album AND c.parentComment IS NULL AND c.status = :status ORDER BY c.createdAt DESC")
    List<AlbumComment> findTopLevelCommentsByAlbumAndStatus(@Param("album") Album album, @Param("status") Status status);
}
