package com.widyu.parentlocation;

import com.widyu.global.entity.BaseTimeEntity;
import com.widyu.global.entity.Status;
import com.widyu.member.Member;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Getter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE parent_location SET status = 'DELETED' WHERE parent_location_id = ?")
@Where(clause = "status = 'ACTIVE'")
public class ParentLocation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parent_location_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LocationType locationType;

    @Column(nullable = false)
    private String placeAddress;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private String name;

    @Enumerated(EnumType.STRING)
    private Status status;

    public void update(LocationType locationType, String placeAddress, Double latitude, Double longitude, String name) {
        this.locationType = locationType;
        this.placeAddress = placeAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.name = name;
    }
}
