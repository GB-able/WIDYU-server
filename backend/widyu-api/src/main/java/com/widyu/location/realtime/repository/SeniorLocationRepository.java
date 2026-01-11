package com.widyu.location.realtime.repository;

import com.widyu.location.SeniorLocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeniorLocationRepository extends CrudRepository<SeniorLocation, Long> {

    Optional<SeniorLocation> findBySeniorId(Long seniorId);

    List<SeniorLocation> findAllBySeniorIdIn(List<Long> seniorIds);
}
