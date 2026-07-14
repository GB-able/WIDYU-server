package com.widyu.fcm.application;

import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.MemberNotificationSetting;
import com.widyu.fcm.NotificationSettingGroup;
import com.widyu.fcm.dto.request.UpdateNotificationSettingRequest;
import com.widyu.fcm.dto.response.NotificationSettingResponse;
import com.widyu.fcm.repository.MemberNotificationSettingRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

    private final MemberNotificationSettingRepository notificationSettingRepository;
    private final MemberUtil memberUtil;

    public List<NotificationSettingResponse> getNotificationSettings() {
        Member member = memberUtil.getCurrentMember();

        Map<FcmCategory, MemberNotificationSetting> settingMap =
                notificationSettingRepository.findAllByMemberId(member.getId())
                        .stream()
                        .collect(Collectors.toMap(
                                MemberNotificationSetting::getCategory,
                                Function.identity()
                        ));

        return NotificationSettingGroup.stream()
                .map(group -> {
                    boolean isGroupEnabled = group.getCategories().stream()
                            .anyMatch(category -> {
                                MemberNotificationSetting setting = settingMap.get(category);
                                // DB에 없으면 기본값 true, 있으면 enabled 값 따름
                                return setting == null || setting.isEnabled();
                            });

                    return NotificationSettingResponse.builder()
                            .group(group.name())
                            .groupName(group.getDescription())
                            .enabled(isGroupEnabled)
                            .build();
                })
                .toList();
    }

    @Transactional
    public NotificationSettingResponse updateNotificationSetting(UpdateNotificationSettingRequest request) {
        Member member = memberUtil.getCurrentMember();

        if (request.group() == null || request.group().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FCM_CATEGORY);
        }

        NotificationSettingGroup group;
        try {
            group = NotificationSettingGroup.valueOf(request.group());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_FCM_CATEGORY); // 재사용, 혹은 새로운 에러 코드 정의
        }

        List<FcmCategory> categoriesInGroup = group.getCategories();
        if (categoriesInGroup.isEmpty()) {
            // ETC와 같이 카테고리가 없는 그룹은 변경 불가
            throw new BusinessException(ErrorCode.INVALID_FCM_CATEGORY);
        }

        Map<FcmCategory, MemberNotificationSetting> existingSettings = notificationSettingRepository
                .findByMemberIdAndCategoryIn(member.getId(), categoriesInGroup)
                .stream()
                .collect(Collectors.toMap(MemberNotificationSetting::getCategory, Function.identity()));

        for (FcmCategory category : categoriesInGroup) {
            MemberNotificationSetting setting = existingSettings.get(category);
            if (setting != null) {
                setting.updateEnabled(request.enabled());
            } else {
                notificationSettingRepository.save(
                        MemberNotificationSetting.create(member, category, request.enabled())
                );
            }
        }

        return NotificationSettingResponse.builder()
                .group(group.name())
                .groupName(group.getDescription())
                .enabled(request.enabled())
                .build();
    }

    public boolean isNotificationEnabled(Long memberId, FcmCategory category) {
        if (category == FcmCategory.ALL) {
            return true;
        }

        return notificationSettingRepository
                .findByMemberIdAndCategory(memberId, category)
                .map(MemberNotificationSetting::isEnabled)
                .orElse(true);
    }
}
