package com.widyu.fcm.repository;

import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.MemberNotificationSetting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberNotificationSettingRepository extends JpaRepository<MemberNotificationSetting, Long> {

    List<MemberNotificationSetting> findAllByMemberId(Long memberId);

    Optional<MemberNotificationSetting> findByMemberIdAndCategory(Long memberId, FcmCategory category);
}