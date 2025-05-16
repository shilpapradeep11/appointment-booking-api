package com.dm.appt.repo;

import com.dm.appt.entity.Colleague;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ColleagueRepository extends JpaRepository<Colleague, Long> {
    Optional<Colleague> findFirstByAvailableTrue();

    List<Colleague> findAll();

    @Query("SELECT c FROM Colleague c WHERE c.skill = :skill ORDER BY " +
            "(SELECT COUNT(a) FROM AppointmentRequest a WHERE a.colleague = c) ASC")
    List<Colleague> findAvailableBySkillOrderedByLoad(@Param("skill") String skill);


}
