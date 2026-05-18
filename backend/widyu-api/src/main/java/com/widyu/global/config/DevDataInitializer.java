package com.widyu.global.config;

import com.widyu.album.Album;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.repository.MedicineRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.medicine.Medicine;
import com.widyu.medicine.MedicineCategory;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.medicine.MedicineScheduleDetail;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.LocalAccount;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.LocalAccountRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.walk.Walk;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * dev/local 프로파일 전용 시드 데이터 초기화.
 *
 * 보호자 로그인: POST /api/v1/auth/guardians/sign-in/local  { email, password }
 * 시니어 로그인:  POST /api/v1/auth/seniors/sign-in          { inviteCode, phoneNumber }
 *
 * 관리자 로그인: POST /api/v1/auth/guardians/sign-in/local  { email, password }
 *   admin@widyu.dev  Test1234!
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  가족 A (familyCode: AAAA01)                                             │
 * │  보호자A1  test_guardian_a1@widyu.dev  Test1234!  leader / representative │
 * │  보호자A2  test_guardian_a2@widyu.dev  Test1234!                          │
 * │  시니어A1  inviteCode: AAAS001  phone: 01011110003                        │
 * │  시니어A2  inviteCode: AAAS002  phone: 01011110004                        │
 * ├──────────────────────────────────────────────────────────────────────────┤
 * │  가족 B (familyCode: BBBB01)                                             │
 * │  보호자B1  test_guardian_b1@widyu.dev  Test1234!  leader / representative │
 * │  보호자B2  test_guardian_b2@widyu.dev  Test1234!                          │
 * │  시니어B1  inviteCode: BBBS001  phone: 01022220003                        │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Component
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private static final String SEED_MARKER_EMAIL = "test_guardian_a1@widyu.dev";
    private static final String ADMIN_EMAIL = "admin@widyu.dev";
    private static final String DEFAULT_PASSWORD = "Test1234!";

    private final MemberRepository memberRepository;
    private final LocalAccountRepository localAccountRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final PasswordEncoder passwordEncoder;

    private final AlbumRepository albumRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineScheduleRepository medicineScheduleRepository;
    private final WalkRepository walkRepository;
    private final HealthScheduleRepository healthScheduleRepository;

    @Override
    public void run(String... args) {
        String encodedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);

        if (!localAccountRepository.existsByEmail(ADMIN_EMAIL)) {
            seedAdmin(encodedPassword);
        }

        if (localAccountRepository.existsByEmail(SEED_MARKER_EMAIL)) {
            if (albumRepository.count() == 0) {
                log.info("[DevDataInitializer] 회원 데이터는 있으나 샘플 데이터 누락 — 보충합니다.");
                reseedSampleData();
            } else {
                log.info("[DevDataInitializer] 시드 데이터 이미 존재합니다. 건너뜁니다.");
            }
            return;
        }

        log.info("[DevDataInitializer] 시드 데이터 초기화 시작...");

        List<Member> familyAseniors = seedFamilyA(encodedPassword);
        List<Member> familyBseniors = seedFamilyB(encodedPassword);

        Member seniorA1 = familyAseniors.get(0);
        Member seniorA2 = familyAseniors.get(1);
        Member seniorB1 = familyBseniors.get(0);

        Medicine vitaminC = findOrSaveMedicine("DEV-MED-001", "비타민C 1000mg", "한미약품");
        Medicine omega3   = findOrSaveMedicine("DEV-MED-002", "오메가3",        "종근당");
        Medicine calcium  = findOrSaveMedicine("DEV-MED-003", "칼슘 마그네슘",   "유한양행");

        seedGoalData(seniorA1, vitaminC, omega3);
        seedGoalData(seniorA2, calcium,  omega3);
        seedGoalData(seniorB1, vitaminC, calcium);

        seedAlbums(seniorA1, seniorA2, seniorB1);

        log.info("[DevDataInitializer] 완료");
    }

    // ===== 회원이 있는데 샘플 데이터만 없는 경우 보충 =====

    private void reseedSampleData() {
        Member seniorA1 = memberRepository.findByPhoneNumber("01011110003").orElse(null);
        Member seniorA2 = memberRepository.findByPhoneNumber("01011110004").orElse(null);
        Member seniorB1 = memberRepository.findByPhoneNumber("01022220003").orElse(null);

        if (seniorA1 == null || seniorA2 == null || seniorB1 == null) {
            log.warn("[DevDataInitializer] 시니어 멤버를 찾을 수 없어 샘플 데이터 보충을 건너뜁니다.");
            return;
        }

        Medicine vitaminC = findOrSaveMedicine("DEV-MED-001", "비타민C 1000mg", "한미약품");
        Medicine omega3   = findOrSaveMedicine("DEV-MED-002", "오메가3",        "종근당");
        Medicine calcium  = findOrSaveMedicine("DEV-MED-003", "칼슘 마그네슘",   "유한양행");

        seedGoalData(seniorA1, vitaminC, omega3);
        seedGoalData(seniorA2, calcium,  omega3);
        seedGoalData(seniorB1, vitaminC, calcium);

        seedAlbums(seniorA1, seniorA2, seniorB1);

        log.info("[DevDataInitializer] 샘플 데이터 보충 완료");
    }

    // ===== 관리자 =====

    @Transactional
    protected void seedAdmin(String encodedPassword) {
        Member admin = memberRepository.save(Member.createAdminMember("관리자", "01000000000"));
        localAccountRepository.save(LocalAccount.createLocalAccount(admin, "admin@widyu.dev", encodedPassword));
    }

    // ===== 회원 / 가족 =====

    @Transactional
    protected List<Member> seedFamilyA(String encodedPassword) {
        Family family = familyRepository.save(Family.createFamily("AAAA01"));

        Member g1 = memberRepository.save(Member.createMember(MemberType.GUARDIAN, "보호자A1", "01011110001"));
        Member g2 = memberRepository.save(Member.createMember(MemberType.GUARDIAN, "보호자A2", "01011110002"));
        Member s1 = memberRepository.save(Member.createMember(MemberType.SENIOR,   "시니어A1", "01011110003"));
        Member s2 = memberRepository.save(Member.createMember(MemberType.SENIOR,   "시니어A2", "01011110004"));

        localAccountRepository.save(LocalAccount.createLocalAccount(g1, "test_guardian_a1@widyu.dev", encodedPassword));
        localAccountRepository.save(LocalAccount.createLocalAccount(g2, "test_guardian_a2@widyu.dev", encodedPassword));

        FamilyMembership m1 = FamilyMembership.createLeaderMembership(family, g1);
        m1.setRepresentative(true);
        familyMembershipRepository.save(m1);
        familyMembershipRepository.save(FamilyMembership.createMembership(family, g2));

        seniorProfileRepository.save(SeniorProfile.createSeniorProfile(s1, family, "서울시 강남구", "101호", "AAAS001"));
        seniorProfileRepository.save(SeniorProfile.createSeniorProfile(s2, family, "서울시 강남구", "202호", "AAAS002"));

        return List.of(s1, s2);
    }

    @Transactional
    protected List<Member> seedFamilyB(String encodedPassword) {
        Family family = familyRepository.save(Family.createFamily("BBBB01"));

        Member g1 = memberRepository.save(Member.createMember(MemberType.GUARDIAN, "보호자B1", "01022220001"));
        Member g2 = memberRepository.save(Member.createMember(MemberType.GUARDIAN, "보호자B2", "01022220002"));
        Member s1 = memberRepository.save(Member.createMember(MemberType.SENIOR,   "시니어B1", "01022220003"));

        localAccountRepository.save(LocalAccount.createLocalAccount(g1, "test_guardian_b1@widyu.dev", encodedPassword));
        localAccountRepository.save(LocalAccount.createLocalAccount(g2, "test_guardian_b2@widyu.dev", encodedPassword));

        FamilyMembership m1 = FamilyMembership.createLeaderMembership(family, g1);
        m1.setRepresentative(true);
        familyMembershipRepository.save(m1);
        familyMembershipRepository.save(FamilyMembership.createMembership(family, g2));

        seniorProfileRepository.save(SeniorProfile.createSeniorProfile(s1, family, "부산시 해운대구", "303호", "BBBS001"));

        return List.of(s1);
    }

    // ===== 약품 =====

    @Transactional
    protected Medicine findOrSaveMedicine(String itemSeq, String itemName, String entpName) {
        return medicineRepository.findByItemSeq(itemSeq)
                .orElseGet(() -> medicineRepository.save(
                        Medicine.create(itemSeq, itemName, entpName, null, null, null)
                ));
    }

    // ===== 목표 데이터 =====

    @Transactional
    protected void seedGoalData(Member senior, Medicine medicine1, Medicine medicine2) {
        seedMedicineSchedules(senior, medicine1, medicine2);
        seedWalks(senior);
        seedHealthSchedules(senior);
    }

    private void seedMedicineSchedules(Member senior, Medicine medicine1, Medicine medicine2) {
        MedicineSchedule morning = MedicineSchedule.create(senior, LocalTime.of(8, 0));
        MedicineCategory morningCategory = MedicineCategory.create("아침");
        morningCategory.addMedicine(MedicineScheduleDetail.create(medicine1, 1));
        morningCategory.addMedicine(MedicineScheduleDetail.create(medicine2, 2));
        morning.addCategory(morningCategory);
        medicineScheduleRepository.save(morning);

        MedicineSchedule evening = MedicineSchedule.create(senior, LocalTime.of(20, 0));
        MedicineCategory eveningCategory = MedicineCategory.create("저녁");
        eveningCategory.addMedicine(MedicineScheduleDetail.create(medicine1, 1));
        evening.addCategory(eveningCategory);
        medicineScheduleRepository.save(evening);
    }

    private void seedWalks(Member senior) {
        LocalDate today = LocalDate.now();

        Walk todayWalk = Walk.createWithGoal(senior, today, 8000);
        todayWalk.updateActualSteps(9200);
        walkRepository.save(todayWalk);

        Walk yesterdayWalk = Walk.createWithGoal(senior, today.minusDays(1), 8000);
        yesterdayWalk.updateActualSteps(4300);
        walkRepository.save(yesterdayWalk);

        Walk threeDaysAgo = Walk.createWithGoal(senior, today.minusDays(3), 8000);
        threeDaysAgo.updateActualSteps(8500);
        walkRepository.save(threeDaysAgo);
    }

    private void seedHealthSchedules(Member senior) {
        LocalDateTime now = LocalDateTime.now();

        healthScheduleRepository.save(HealthSchedule.create(
                senior, "건강검진",
                "서울시 강남구 테헤란로 123 강남세브란스병원",
                "37.5013", "127.0262", now.plusWeeks(2)
        ));
        healthScheduleRepository.save(HealthSchedule.create(
                senior, "치과 정기검진",
                "서울시 강남구 역삼동 456 연세치과",
                "37.4979", "127.0276", now.plusMonths(1)
        ));
    }

    // ===== 앨범 =====

    @Transactional
    protected void seedAlbums(Member seniorA1, Member seniorA2, Member seniorB1) {
        albumRepository.save(Album.createAlbumWithMetadata(
                seniorA1, "오늘 공원 산책했어요",
                List.of("https://picsum.photos/seed/a1p1/600/400"),
                List.of("https://picsum.photos/seed/a1p1/300/200"),
                List.of(0)
        ));
        albumRepository.save(Album.createAlbumWithMetadata(
                seniorA1, "손자랑 점심 먹었어요",
                List.of("https://picsum.photos/seed/a1p2/600/400",
                        "https://picsum.photos/seed/a1p3/600/400"),
                List.of("https://picsum.photos/seed/a1p2/300/200",
                        "https://picsum.photos/seed/a1p3/300/200"),
                List.of(0, 0)
        ));

        albumRepository.save(Album.createAlbumWithMetadata(
                seniorA2, "꽃이 피었어요",
                List.of("https://picsum.photos/seed/a2p1/600/400"),
                List.of("https://picsum.photos/seed/a2p1/300/200"),
                List.of(0)
        ));

        albumRepository.save(Album.createAlbumWithMetadata(
                seniorB1, "오늘 날씨 좋다",
                List.of("https://picsum.photos/seed/b1p1/600/400"),
                List.of("https://picsum.photos/seed/b1p1/300/200"),
                List.of(0)
        ));
        albumRepository.save(Album.createAlbumWithMetadata(
                seniorB1, "저녁 밥상",
                List.of("https://picsum.photos/seed/b1p2/600/400",
                        "https://picsum.photos/seed/b1p3/600/400"),
                List.of("https://picsum.photos/seed/b1p2/300/200",
                        "https://picsum.photos/seed/b1p3/300/200"),
                List.of(0, 0)
        ));
    }
}
