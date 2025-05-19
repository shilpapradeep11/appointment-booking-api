package com.dm.appt.impl;

import com.dm.appt.email.EmailService;
import com.dm.appt.entity.AppointmentRequest;
import com.dm.appt.entity.Colleague;
import com.dm.appt.repo.AppointmentRepository;
import com.dm.appt.repo.ColleagueRepository;
import com.dm.appt.service.AppointmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class AppointmentServiceImpl {
    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ColleagueRepository colleagueRepository;

    @Autowired
    private EmailService emailService;


    private final java.util.Queue<AppointmentRequest> manualQueue = new ConcurrentLinkedQueue<>();

    @RabbitListener(queues = "appointmentQueue")
    public void handleAppointment(AppointmentRequest incomingRequest) {
        try {
            AppointmentRequest request = appointmentRepository.findById(incomingRequest.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + incomingRequest.getId()));

            float urgencyScore = appointmentService.predictUrgency(request);
            ObjectMapper debugMapper = new ObjectMapper().registerModule(new JavaTimeModule());
            System.out.println("[Rabbit] Received: " + debugMapper.writeValueAsString(incomingRequest));

            if (request.getRequestedDate() == null) {
                System.err.println("[ERROR] Requested date is null in predictUrgency()");
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime requested = request.getRequestedDate();

            if (urgencyScore > 0.5 && requested.isBefore(now.plusHours(24))) {
                List<Colleague> colleagues = colleagueRepository.findAll();
                List<Colleague> availableColleagues = new ArrayList<>();

                for (Colleague colleague : colleagues) {
                    LocalDateTime start = requested.minusMinutes(30);
                    LocalDateTime end = requested.plusMinutes(30);
                    boolean conflict = appointmentRepository.hasConflict(colleague, start, end);
                    if (!conflict) {
                        availableColleagues.add(colleague);
                    }
                }

                if (!availableColleagues.isEmpty()) {
                    Colleague selected = availableColleagues.get(new Random().nextInt(availableColleagues.size()));
                    request.setColleague(selected);
                    request.setAllocatedTo(selected.getName());
                    request.setStatus("Auto-allocated");
                    System.out.println("[INFO] Appointment auto-allocated: " + request.getApplicationType());
                } else {
                    request.setStatus("Manual Review Notified");
                    manualQueue.add(request);
                    System.out.println("[WARN] No available colleague. Queued for manual allocation.");
                    request.setResponseMessage("No available colleague. Queued for manual allocation");
                }

            } else {
                request.setStatus("Manual Review Notified");
                manualQueue.add(request);
                System.out.println("[INFO] Appointment flagged for manual review (>24hrs): " + request.getApplicationType());
                System.out.println("[WARN] No available colleague. Queued for manual allocation."+request.getResponseMessage());
            }

            System.out.println("[DEBUG] Saving with status: " + request.getStatus());
            System.out.println("[WARN] No available colleague. Queued for manual allocation."+request.getResponseMessage());
            appointmentRepository.save(request);

            //Send email only if the status is either 'Manually Allocated' or 'Auto-allocated'
            if(request.status.equalsIgnoreCase("Manually Allocated") || request.status.equalsIgnoreCase("Auto-allocated")) {
                System.out.println("****** Status ***** "+request.status);
                if (request.getCustomer() != null && request.getCustomer().getEmail() != null) {
                    String email = request.getCustomer().getEmail();
                    String subject = "Your Appointment Has Been Confirmed";
                    String body = String.format(
                            "Dear %s,\n\nYour appointment for %s via %s has been scheduled on %s.\n\nRegards,\nAppointment Team",
                            request.getCustomer().getFirstName(),
                            request.getApplicationType(),
                            request.getInteractionMethod(),
                            request.getRequestedDate().toString()
                    );
                    emailService.sendAppointmentNotification(email, subject, body);
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
            incomingRequest.setStatus("ML Failure - Manual Required");
            manualQueue.add(incomingRequest);
            appointmentRepository.save(incomingRequest);
            System.out.println("[⚠️ Fallback] ML failed — appointment manually queued: " + incomingRequest.getApplicationType());
        }
    }


    /*@Scheduled(fixedRate = 60000)
    public void checkManualQueue() {
        while (!manualQueue.isEmpty()) {
            AppointmentRequest request = manualQueue.poll();
            System.out.println("[MANUAL] Manual allocation required for: " + request.applicationType);
            request.status = "Manual Review Notified";
            Optional<AppointmentRequest> existing = appointmentRepository.findById(request.id);
            if (existing.isPresent()) {
                AppointmentRequest attached = existing.get();
                attached.status = request.status;
                appointmentRepository.save(attached);
            }
        }
    }*/
}
