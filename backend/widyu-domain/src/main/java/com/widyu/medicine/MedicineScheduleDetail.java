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

    @Column(nullable = false)
    private Integer dose;

    @Builder(access = AccessLevel.PRIVATE)
    private MedicineScheduleDetail(Medicine medicine, Integer dose) {
        this.medicine = medicine;
        this.dose = dose;
    }

    public static MedicineScheduleDetail create(Medicine medicine, Integer dose) {
        return MedicineScheduleDetail.builder()
                .medicine(medicine)
                .dose(dose)
                .build();
    }

    public void updateDose(Integer dose) {
        this.dose = dose;
    }
}
