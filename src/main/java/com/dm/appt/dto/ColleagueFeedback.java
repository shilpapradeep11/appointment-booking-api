package com.dm.appt.dto;

import lombok.Data;

@Data
public class ColleagueFeedback {
    private int rating;
    private String comment;
    private Long colleagueId;
}

