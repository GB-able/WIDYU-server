package com.widyu.admin.application;

import com.widyu.admin.dto.response.AdminAlbumResponse;
import com.widyu.admin.dto.response.AdminPageResponse;
import com.widyu.album.repository.AlbumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAlbumService {

    private final AlbumRepository albumRepository;

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminAlbumResponse> getAlbumPage(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return AdminPageResponse.from(
                albumRepository.findAllForAdmin(pageRequest).map(AdminAlbumResponse::from)
        );
    }
}
