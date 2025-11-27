package com.widyu.medicine;

import com.widyu.global.entity.BaseTimeEntity;
import com.widyu.global.entity.Status;
import com.widyu.member.Member;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicineSchedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private LocalTime alarmTime;

    @OneToMany(mappedBy = "medicineSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MedicineCategory> categories = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Builder(access = AccessLevel.PRIVATE)
    private MedicineSchedule(Member member, LocalTime alarmTime) {
        this.member = member;
        this.alarmTime = alarmTime;
        this.status = Status.ACTIVE;
    }

    public static MedicineSchedule create(Member member, LocalTime alarmTime) {
        return MedicineSchedule.builder()
                .member(member)
                .alarmTime(alarmTime)
                .build();
    }

    public void addCategory(MedicineCategory category) {
        this.categories.add(category);
        category.setMedicineSchedule(this);
    }

    public void updateAlarmTime(LocalTime alarmTime) {
        this.alarmTime = alarmTime;
    }

    public void delete() {
        this.status = Status.DELETED;
    }

    public Integer getTotalCount() {
        return categories.stream()
                .mapToInt(MedicineCategory::getCountSum)
                .sum();
    }
}
