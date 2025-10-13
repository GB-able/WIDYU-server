package com.widyu.domain.fcm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.widyu.domain.fcm.api.dto.FcmMessageDto;
import com.widyu.domain.fcm.api.dto.FcmSendDto;
import com.widyu.domain.fcm.api.dto.response.FcmCategoryResponse;
import com.widyu.domain.fcm.api.dto.response.FcmNotificationResponses;
import com.widyu.domain.fcm.api.dto.response.FcmSendResponse;
import com.widyu.domain.fcm.api.dto.response.ToastResDto;
import com.widyu.domain.fcm.domain.FcmCategory;
import com.widyu.domain.fcm.domain.FcmNotification;
import com.widyu.domain.fcm.domain.MemberFcmToken;
import com.widyu.domain.fcm.domain.repository.FcmNotificationRepository;
import com.widyu.domain.fcm.domain.repository.MemberFcmTokenRepository;
import com.widyu.domain.album.repository.AlbumViewRepository;
import com.widyu.domain.member.repository.ParentProfileRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.domain.member.entity.Member;
import com.widyu.domain.member.entity.ParentProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcmService {

    @Value("${firebase.config-path}")
    private String firebaseConfigPath;

    private final FcmNotificationRepository fcmNotificationRepository;
    private final MemberFcmTokenRepository memberFcmTokenRepository;
    private final AlbumViewRepository albumViewRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final MemberUtil memberUtil;
    private static final String API_URL = "https://fcm.googleapis.com/v1/projects/widyu-d384f/messages:send";

    private String makeMessage(String token, FcmSendDto dto) throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();

        FcmMessageDto fcmMessageDto = FcmMessageDto.builder()
                .message(FcmMessageDto.Message.builder()
                        .token(token)
                        .notification(FcmMessageDto.Notification.builder()
                                .title(dto.title())
                                .body(dto.content())
                                .image(null)
                                .build())
                        .build())
                .validateOnly(false)
                .build();

        return om.writeValueAsString(fcmMessageDto);
    }

    private String getAccessToken() throws IOException {
        GoogleCredentials googleCredentials = GoogleCredentials
                .fromStream(new ClassPathResource(firebaseConfigPath).getInputStream())
                .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));

        googleCredentials.refreshIfExpired();

        return googleCredentials.getAccessToken().getTokenValue();
    }

    // 카테고리 및 커서 기반 알림 목록 조회
    public FcmNotificationResponses getNotificationsForCurrentUser(String category, Long cursor) {
        Member member = memberUtil.getCurrentMember();

        int pageSize = 10;
        int fetchSize = pageSize + 1;
        Pageable pageable = PageRequest.of(0, fetchSize);

        List<FcmNotification> notifications;

        if ("ALL".equals(category)) {
            notifications = fcmNotificationRepository.findNotificationsWithCursor(
                    member.getId(), cursor, pageable);
        } else {
            try {
                FcmCategory fcmCategory = FcmCategory.valueOf(category);
                notifications = fcmNotificationRepository.findNotificationsByCategoryWithCursor(
                        member.getId(), fcmCategory, cursor, pageable);
            } catch (IllegalArgumentException e) {
                // 잘못된 카테고리인 경우 전체 조회로 처리
                notifications = fcmNotificationRepository.findNotificationsWithCursor(
                        member.getId(), cursor, pageable);
            }
        }

        if (notifications.isEmpty()) {
            return FcmNotificationResponses.empty();
        }

        boolean hasNext = notifications.size() > pageSize;
        List<FcmNotification> pageNotifications = hasNext ?
                notifications.subList(0, pageSize) : notifications;

        Long nextCursor = hasNext ? pageNotifications.get(pageSize - 1).getId() : null;

        return FcmNotificationResponses.of(pageNotifications, hasNext, nextCursor);
    }

    // 알림 개별 읽기
    @Transactional
    public String markAsRead(Long notificationId) {
        Member member = memberUtil.getCurrentMember();
        FcmNotification notification = fcmNotificationRepository
                .findByIdAndMemberFcmToken_MemberId(notificationId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FCM_NOTIFICATION_NOT_FOUND));

        if (!notification.isRead()) {
            notification.markAsRead();
            return "알림 읽음 처리 성공";
        } else {
            return "이미 읽은 알림입니다";
        }
    }

    @Transactional
    public void sendMessageToUser(Long memberId, FcmSendDto fcmSendDto) {
        try {
            List<MemberFcmToken> tokens = memberFcmTokenRepository.findAllByMemberIdAndActiveTrue(memberId);

            for (MemberFcmToken tokenEntity : tokens) {
                String token = tokenEntity.getToken();
                String message = makeMessage(token, fcmSendDto);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(getAccessToken());

                HttpEntity<String> entity = new HttpEntity<>(message, headers);
                RestTemplate restTemplate = new RestTemplate();
                restTemplate.getMessageConverters()
                        .add(0, new StringHttpMessageConverter(
                                StandardCharsets.UTF_8));

                ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.POST, entity, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    fcmNotificationRepository.save(FcmNotification.builder()
                            .title(fcmSendDto.title())
                            .body(fcmSendDto.content())
                            .fcmCategory(FcmCategory.ALBUM)
                            .memberFcmToken(tokenEntity)
                            .isRead(false)
                            .build());
                }
            }
        } catch (IOException e) {
            log.error("Failed to send FCM message to user {}: {}", memberId, e.getMessage());
        }
    }

    public List<FcmCategoryResponse> getNotificationCategories() {
        Member member = memberUtil.getCurrentMember();

        return Arrays.stream(FcmCategory.values())
                .map(category -> {
                    long count = switch (category) {
                        case ALL -> fcmNotificationRepository.countByMemberFcmToken_MemberIdAndIsReadFalse(member.getId());
                        default -> fcmNotificationRepository.countByMemberFcmToken_MemberIdAndFcmCategoryAndIsReadFalse(member.getId(), category);
                    };
                    return FcmCategoryResponse.of(category, count);
                })
                .toList();
    }

    // 토스트 모달 알림
    public ToastResDto getToastNotification() {
        Member member = memberUtil.getCurrentMember();

        List<ParentProfile> parentProfiles = parentProfileRepository.findAllByGuardianId(member.getId());
        if (parentProfiles.isEmpty()) {
            return null;
        }

        int[] thresholds = {0, 2, 4, 6};

        for (int threshold : thresholds) {
            for (ParentProfile parentProfile : parentProfiles) {
                long unviewedCount = calculateUnviewedCount(member.getId(), parentProfile);

                if ((threshold == 0 && unviewedCount == 0) ||
                    (threshold > 0 && unviewedCount > 0 && unviewedCount <= threshold)) {
                    return createToastMessage(parentProfile.getMember().getName(), unviewedCount);
                }
            }
        }

        return null;
    }

    private long calculateUnviewedCount(Long guardianId, ParentProfile parentProfile) {
        Long parentMemberId = parentProfile.getMember().getId();
        // 내가 올린 전체 앨범 수
        long totalCount = albumViewRepository.countTotalAlbumsByParent(guardianId);
        // 부모님이 내 앨범을 본 수
        long viewedCount = albumViewRepository.countViewedAlbumsByGuardianAndParent(parentMemberId, guardianId);

        return totalCount - viewedCount;
    }

    private ToastResDto createToastMessage(String parentName, long unviewedCount) {
        if (unviewedCount == 0) {
            return ToastResDto.from(parentName + "님께서 모든 소식을 다 보셨어요.");
        }
            return ToastResDto.from(parentName + "님께서 보실 소식이 " + unviewedCount + "개밖에 남지 않았어요.");
    }

}
