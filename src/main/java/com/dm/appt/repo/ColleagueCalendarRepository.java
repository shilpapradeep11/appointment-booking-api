package com.dm.appt.repo;

import com.dm.appt.entity.Colleague;
import com.dm.appt.entity.ColleagueCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ColleagueCalendarRepository extends JpaRepository<ColleagueCalendar, Long> {

    @Query("SELECT c.colleague.name, c.date, COUNT(c) " +
            "FROM ColleagueCalendar c " +
            "WHERE c.startTime >= :startTime AND c.endTime <= :endTime AND c.status = 'BUSY' " +
            "GROUP BY c.colleague.name, c.date")
    List<Object[]> countAppointmentsPerDayForEachColleague(
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM ColleagueCalendar c " +
            "WHERE c.colleague = :colleague " +
            "AND c.date = :date " +
            "AND c.status = 'BUSY' " +
            "AND ((c.startTime <= :startTime AND c.endTime > :startTime) " +
            "  OR (c.startTime < :endTime AND c.endTime >= :endTime) " +
            "  OR (c.startTime >= :startTime AND c.endTime <= :endTime))")
    boolean hasCalendarConflict(@Param("colleague") Colleague colleague,
                                @Param("date") LocalDate date,
                                @Param("startTime") LocalTime startTime,
                                @Param("endTime") LocalTime endTime);


}

