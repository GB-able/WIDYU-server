package com.widyu.admin.application;

import com.widyu.admin.dto.response.AdminDashboardResponse;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.heart.repository.HeartRateEmergencyRepository;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.pay.PaymentStatus;
import com.widyu.pay.repository.PaymentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final MemberRepository memberRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final AlbumRepository albumRepository;
    private final PaymentRepository paymentRepository;
    private final HeartRateEmergencyRepository heartRateEmergencyRepository;

    public AdminDashboardResponse getDashboard() {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(zone);

        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        ZonedDateTime todayStartZdt = todayStart.atZone(zone);
        ZonedDateTime monthStartZdt = monthStart.atZone(zone);

        return new AdminDashboardResponse(
                memberRepository.count(),
                memberRepository.countByType(MemberType.SENIOR),
                memberRepository.countByType(MemberType.GUARDIAN),
                memberRepository.countByCreatedAtAfter(todayStart),
                familyMembershipRepository.count(),
                albumRepository.countNewAlbums(todayStart),
                albumRepository.countNewAlbums(weekStart),
                albumRepository.countNewAlbums(monthStart),
                paymentRepository.sumAmountSince(todayStartZdt, PaymentStatus.DONE),
                paymentRepository.sumAmountSince(monthStartZdt, PaymentStatus.DONE),
                heartRateEmergencyRepository.count()
        );
    }
}
