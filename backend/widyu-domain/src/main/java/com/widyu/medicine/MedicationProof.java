package com.widyu.medicine;

import com.widyu.global.entity.BaseTimeEntity;
import com.widyu.member.Member;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationProof extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_schedule_id", nullable = false)
    private MedicineSchedule medicineSchedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ElementCollection
    @CollectionTable(name = "medication_proof_images",
                    joinColumns = @JoinColumn(name = "medication_proof_id"))
    @Column(name = "image_url")
    private List<String> proofImageUrls = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private MedicationProof(MedicineSchedule medicineSchedule, Member member,
                            List<String> proofImageUrls, LocalDateTime verifiedAt) {
        this.medicineSchedule = medicineSchedule;
        this.member = member;
        this.proofImageUrls = proofImageUrls != null ? proofImageUrls : new ArrayList<>();
        this.verifiedAt = verifiedAt != null ? verifiedAt : LocalDateTime.now();
    }

    public static MedicationProof create(MedicineSchedule medicineSchedule, Member member,
                                         List<String> proofImageUrls) {
        return MedicationProof.builder()
                .medicineSchedule(medicineSchedule)
                .member(member)
                .proofImageUrls(proofImageUrls)
                .verifiedAt(LocalDateTime.now())
                .build();
    }
}
