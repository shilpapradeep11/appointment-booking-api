package com.dm.appt.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllocationRequest {
    private Long appointmentId;
    private Long colleagueId;
    private String applicationType;  // e.g., "Insurance"
}
