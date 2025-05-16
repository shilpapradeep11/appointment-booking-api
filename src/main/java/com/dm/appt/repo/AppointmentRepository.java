package com.dm.appt.repo;

import com.dm.appt.dto.AppointmentWithColleague;
import com.dm.appt.entity.AppointmentRequest;
import com.dm.appt.entity.Colleague;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<AppointmentRequest, Long> {
    List<AppointmentRequest> findByAllocatedTo(String allocatedTo);

    List<AppointmentRequest> findByColleagueAndRequestedDate(Colleague colleague, LocalDateTime requestedDate);

    Optional<AppointmentRequest> findTopByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Page<AppointmentRequest> findByStatusIgnoreCase(String status, Pageable pageable);

    @Query("SELECT a FROM AppointmentRequest a LEFT JOIN FETCH a.colleague " +
            "WHERE LOWER(a.status) IN ('manual review notified', 'manually allocated')")
    List<AppointmentRequest> findWithColleagueByStatus(@Param("status") String status);


    @Query("SELECT new com.dm.appt.dto.AppointmentWithColleague(" +
            "a.id, a.applicationType, a.interactionMethod, a.requestedDate, a.status, " +
            "CASE WHEN a.colleague.name IS NULL THEN 'Unassigned' ELSE a.colleague.name END, " +
            "CASE WHEN a.colleague.available IS NULL THEN false ELSE a.colleague.available END) " +
            "FROM AppointmentRequest a " +
            "LEFT JOIN a.colleague c " +
            "WHERE LOWER(a.status) = 'manual review notified'")
    List<AppointmentWithColleague> findAppointmentsWithColleagueForManualReview();

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
            "FROM AppointmentRequest a " +
            "WHERE a.colleague = :colleague " +
            "AND a.requestedDate BETWEEN :start AND :end")
    boolean hasConflict(@Param("colleague") Colleague colleague,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

}
