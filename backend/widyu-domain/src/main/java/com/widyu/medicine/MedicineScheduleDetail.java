package com.widyu.medicine;

import com.widyu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicineScheduleDetail extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_category_id", nullable = false)
    private MedicineCategory medicineCategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    // 0.5정 단위(반 알약)를 담는다. 0.5의 배수는 이진 부동소수로 정확히 표현돼 합산해도 오차가 없다 (#1001).
    @Column(nullable = false)
    private Double dose;

    @Builder(access = AccessLevel.PRIVATE)
    private MedicineScheduleDetail(Medicine medicine, Double dose) {
        this.medicine = medicine;
        this.dose = dose;
    }

    public static MedicineScheduleDetail create(Medicine medicine, Double dose) {
        return MedicineScheduleDetail.builder()
                .medicine(medicine)
                .dose(dose)
                .build();
    }

    public void updateDose(Double dose) {
        this.dose = dose;
    }
}
