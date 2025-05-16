package com.dm.appt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Data
public class ColleagueCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Colleague colleague;

    private LocalDate date;

    private LocalTime startTime;

    private LocalTime endTime;

    // status could be "BUSY", "FREE", or used as needed
    private String status;
}

