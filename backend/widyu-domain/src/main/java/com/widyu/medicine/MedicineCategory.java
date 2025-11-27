package com.widyu.medicine;

import com.widyu.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicineCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_schedule_id", nullable = false)
    private MedicineSchedule medicineSchedule;

    @Column(nullable = false, length = 100)
    private String name;

    @OneToMany(mappedBy = "medicineCategory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MedicineScheduleDetail> medicines = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private MedicineCategory(String name) {
        this.name = name;
    }

    public static MedicineCategory create(String name) {
        return MedicineCategory.builder()
                .name(name)
                .build();
    }

    public void addMedicine(MedicineScheduleDetail detail) {
        this.medicines.add(detail);
        detail.setMedicineCategory(this);
    }

    public Integer getCountSum() {
        return medicines.stream()
                .mapToInt(MedicineScheduleDetail::getDose)
                .sum();
    }

    public void updateName(String name) {
        this.name = name;
    }
}
