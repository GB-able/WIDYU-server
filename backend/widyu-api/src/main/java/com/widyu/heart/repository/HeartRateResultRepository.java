package com.widyu.heart.repository;

import com.widyu.heart.HeartRateResult;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HeartRateResultRepository extends CrudRepository<HeartRateResult, Long> {

    Optional<HeartRateResult> findByMemberId(Long memberId);
}
