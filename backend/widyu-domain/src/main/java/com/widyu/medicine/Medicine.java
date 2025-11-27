package com.widyu.medicine;

import com.widyu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medicine extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String usage;

    @Column(columnDefinition = "TEXT")
    private String efficacy;

    @Builder(access = AccessLevel.PRIVATE)
    private Medicine(String name, String imageUrl, String description, String usage, String efficacy) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.description = description;
        this.usage = usage;
        this.efficacy = efficacy;
    }

    public static Medicine create(String name, String imageUrl, String description,
                                   String usage, String efficacy) {
        return Medicine.builder()
                .name(name)
                .imageUrl(imageUrl)
                .description(description)
                .usage(usage)
                .efficacy(efficacy)
                .build();
    }
}
