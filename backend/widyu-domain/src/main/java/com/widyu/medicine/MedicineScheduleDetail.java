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

    @Column(length = 10)
    private String unit;

    @Builder(access = AccessLevel.PRIVATE)
    private MedicineScheduleDetail(Medicine medicine, Integer dose, String unit) {
        this.medicine = medicine;
        this.dose = dose;
        this.unit = unit;
    }

    public static MedicineScheduleDetail create(Medicine medicine, Integer dose, String unit) {
        return MedicineScheduleDetail.builder()
                .medicine(medicine)
                .dose(dose)
                .unit(unit)
                .build();
    }

    public void updateDose(Integer dose) {
        this.dose = dose;
    }

    public void updateUnit(String unit) {
        this.unit = unit;
    }
}
