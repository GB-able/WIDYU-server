package com.widyu.fcm.application;

import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.MemberNotificationSetting;
import com.widyu.fcm.dto.request.UpdateNotificationSettingRequest;
import com.widyu.fcm.dto.response.NotificationSettingResponse;
import com.widyu.fcm.repository.MemberNotificationSettingRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
                                setting -> setting
                        ));

        return Arrays.stream(FcmCategory.values())
                .filter(category -> category != FcmCategory.ALL)
                .map(category -> {
                    MemberNotificationSetting setting = settingMap.get(category);
                    if (setting != null) {
                        return NotificationSettingResponse.from(setting);
                    }
                    return NotificationSettingResponse.ofDefault(category);
                })
                .toList();
    }

    @Transactional
    public NotificationSettingResponse updateNotificationSetting(UpdateNotificationSettingRequest request) {
        Member member = memberUtil.getCurrentMember();

        FcmCategory category;
        try {
            category = FcmCategory.valueOf(request.category());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_FCM_CATEGORY);
        }

        if (category == FcmCategory.ALL) {
            throw new BusinessException(ErrorCode.INVALID_FCM_CATEGORY);
        }

        MemberNotificationSetting setting = notificationSettingRepository
                .findByMemberIdAndCategory(member.getId(), category)
                .orElse(null);

        if (setting == null) {
            setting = notificationSettingRepository.save(
                    MemberNotificationSetting.create(member, category, request.enabled())
            );
        } else {
            setting.updateEnabled(request.enabled());
        }

        return NotificationSettingResponse.from(setting);
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