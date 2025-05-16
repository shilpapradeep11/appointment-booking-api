package com.dm.appt.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Data
@AllArgsConstructor
public class AppointmentRequest implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "colleague_id")
    private Colleague colleague;

    public String applicationType;
    public String interactionMethod;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    public LocalDateTime requestedDate;

    public String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    public LocalDateTime createdAt;

    public String allocatedTo;
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;


}
