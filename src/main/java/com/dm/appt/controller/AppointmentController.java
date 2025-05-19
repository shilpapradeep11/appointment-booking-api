package com.dm.appt.controller;

import com.dm.appt.dto.AllocationRequest;
import com.dm.appt.dto.AppointmentWithColleague;
import com.dm.appt.email.EmailService;
import com.dm.appt.entity.AppointmentRequest;
import com.dm.appt.entity.Colleague;
import com.dm.appt.entity.Customer;
import com.dm.appt.impl.AppointmentServiceImpl;
import com.dm.appt.repo.AppointmentRepository;
import com.dm.appt.repo.ColleagueCalendarRepository;
import com.dm.appt.repo.ColleagueRepository;
import com.dm.appt.repo.CustomerRepository;
import com.dm.appt.service.AppointmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class AppointmentController {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private ColleagueRepository colleagueRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ColleagueCalendarRepository calendarRepo;


    @SneakyThrows
    @PostMapping("/appointment")
    public ResponseEntity<String> submitAppointment(@RequestBody AppointmentRequest request) {

        if (request.getCustomer() == null || request.getCustomer().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer ID is missing");
        }

        Customer customer = customerRepository.findById(request.getCustomer().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        request.setCustomer(customer);

        // 🔁 Pre-check BEFORE saving or sending to RabbitMQ
        boolean available = appointmentService.checkIfAnyColleagueAvailableWithin24Hrs(request);

        if (!available) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("⚠️ No available colleague at this time. Please choose a different date/time.");
        }

        request.setCreatedAt(LocalDateTime.now());
        request.setStatus("Received");

        // ✅ Save only if available
        AppointmentRequest saved = appointmentRepository.save(request);

        ObjectMapper debugMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        System.out.println("[Rabbit] Sending: " + debugMapper.writeValueAsString(saved));

        rabbitTemplate.convertAndSend("appointmentExchange", "appointment.request", saved);

        return ResponseEntity.ok("Appointment received");
    }


    @GetMapping("/manual-review-appointments")
    public ResponseEntity<Page<AppointmentWithColleague>> getManualReviewAppointments(@RequestParam(defaultValue = "0") int page,
                                                                                      @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AppointmentWithColleague> pageResult = appointmentService.getManualReviewAppointments(pageable);
        return ResponseEntity.ok(pageResult);

    }

    @PostMapping("/allocate-colleague")
    public ResponseEntity<Void> allocateColleagueToAppointment(@RequestBody AllocationRequest request) {
        AppointmentRequest appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        LocalDateTime requested = appointment.getRequestedDate();
        LocalDate date = requested.toLocalDate();
        LocalTime start = requested.toLocalTime().minusMinutes(30);
        LocalTime end = requested.toLocalTime().plusMinutes(30);

        List<Colleague> skilled = colleagueRepository.findAvailableBySkillOrderedByLoad(request.getApplicationType());
        Colleague selected = null;

        // 1. Check skilled + free
        for (Colleague c : skilled) {
            boolean conflict = calendarRepo.hasCalendarConflict(c, date, start, end);
            if (!conflict) {
                selected = c;
                System.out.println("Skilled + Free - No Conflit "+selected);
                break;
            }
        }

        // 2. Fallback: any colleague who's free
        if (selected == null) {
            for (Colleague c : colleagueRepository.findAll()) {
                boolean conflict = calendarRepo.hasCalendarConflict(c, date, start, end);
                if (!conflict) {
                    selected = c;
                    System.out.println("Fallback + Any Free Colleague - No Conflit "+selected);
                    break;
                }
            }
        }

        // 3. If still null, throw
        if (selected == null) {
            System.out.println("No colleague available at that time");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No colleague available at that time");
        }

        System.out.println("Assigned colleague " + selected.getName() + " (jobCode: " + selected.getJobCode() + ")");
        appointment.setColleague(selected);
        appointment.setAllocatedTo(selected.getName());
        appointment.setStatus("Manually Allocated");
        appointmentRepository.save(appointment);

        //Send email only if the status is either 'Manually Allocated' or 'Auto-allocated'
        if(appointment.status.equalsIgnoreCase("Manually Allocated")) {
            System.out.println("****** Status ***** "+appointment.status);
            if (appointment.getCustomer() != null && appointment.getCustomer().getEmail() != null) {
                String email = appointment.getCustomer().getEmail();
                String subject = "Your Appointment Has Been Confirmed";
                String body = String.format(
                        "Dear %s,\n\nYour appointment for %s via %s has been scheduled on %s.\n\nRegards,\nAppointment Team",
                        appointment.getCustomer().getFirstName(),
                        appointment.getApplicationType(),
                        appointment.getInteractionMethod(),
                        appointment.getRequestedDate().toString()
                );
                emailService.sendAppointmentNotification(email, subject, body);
            }
        }

        return ResponseEntity.ok().build();
    }


}
