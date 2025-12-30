package com.widyu.location.parentlocation.repository;

import com.widyu.member.Member;
import com.widyu.parentlocation.LocationType;
import com.widyu.parentlocation.ParentLocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentLocationRepository extends JpaRepository<ParentLocation, Long> {

    boolean existsByMemberAndPlaceAddress(Member member, String placeAddress);

    Optional<ParentLocation> findByIdAndMember(Long id, Member member);

    List<ParentLocation> findAllByMember(Member member);
}
