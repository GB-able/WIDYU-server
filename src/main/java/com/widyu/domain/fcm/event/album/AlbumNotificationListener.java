package com.widyu.domain.fcm.event.album;

import com.widyu.domain.fcm.application.FcmService;
import com.widyu.domain.fcm.api.dto.FcmSendDto;
import com.widyu.domain.fcm.domain.FcmCategory;
import com.widyu.domain.member.entity.Member;
import com.widyu.domain.member.entity.ParentProfile;
import com.widyu.domain.member.repository.MemberRepository;
import com.widyu.domain.member.repository.ParentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AlbumNotificationListener {

    private final FcmService fcmService;
    private final ParentProfileRepository parentProfileRepository;
    private final MemberRepository memberRepository;

    @Async
    @EventListener
    public void handleAlbumCreated(AlbumCreatedEvent event) {
        // 보호자 정보 조회
        Member guardian = memberRepository.findById(event.authorId())
                .orElse(null);
        if (guardian == null) {
            return;
        }

        // 보호자의 부모님 목록 조회
        List<ParentProfile> parentProfiles = parentProfileRepository.findAllByGuardianId(event.authorId());

        // 각 부모님에게 알림 발송
        for (ParentProfile parentProfile : parentProfiles) {
            FcmSendDto dto = new FcmSendDto(
                    guardian.getName() + "님이 새로운 소식을 전했어요!",
                    "새로운 앨범을 확인해보세요.",
                    FcmCategory.ALBUM
                    ,""
            );
            fcmService.sendMessageToUser(parentProfile.getMember().getId(), dto);
        }
    }
}
