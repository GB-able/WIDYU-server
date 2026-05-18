package com.widyu.member.repository;

import com.widyu.member.Family;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyRepository extends JpaRepository<Family, Long> {

    Optional<Family> findByFamilyCode(String familyCode);

    boolean existsByFamilyCode(String familyCode);
}
