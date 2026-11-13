package com.widyu.location.stay.repository;

import com.widyu.location.stay.SeniorStayState;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeniorStayStateRepository extends JpaRepository<SeniorStayState, Long> {

    @Modifying
    @Query("delete from SeniorStayState s where s.memberId = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);

    @Query("select s.memberId from SeniorStayState s where not exists (select m.id from Member m "
            + "where m.id = s.memberId and m.status = com.widyu.global.entity.Status.ACTIVE)")
    List<Long> findInactiveMemberIds();
}
