package com.dm.appt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AppointmentWithColleague {
    private Long appointmentId;
    private String applicationType;
    private String interactionMethod;
    private LocalDateTime requestedDate;
    private String status;
    private String colleagueName;
    private boolean colleagueAvailable;

}

