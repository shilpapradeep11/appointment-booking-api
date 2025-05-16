package com.dm.appt.controller;

import com.dm.appt.dto.AppointmentWithColleague;
import com.dm.appt.dto.ColleagueFeedback;
import com.dm.appt.entity.Colleague;
import com.dm.appt.repo.ColleagueCalendarRepository;
import com.dm.appt.repo.ColleagueRepository;
import com.dm.appt.service.ColleagueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class ColleagueController {

    @Autowired
    ColleagueRepository repository;

    @Autowired
    ColleagueService colleagueService;

    @Autowired
    private ColleagueCalendarRepository calendarRepo;

    @GetMapping("/colleagueDetails")
    public ResponseEntity<List<Colleague>> getManualReviewAppointments() {

        List<Colleague> result = repository.findAll();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/calendar/count-per-day")
    public List<Map<String, Object>> getDailyCounts() {
        List<Object[]> data = calendarRepo.countAppointmentsPerDayForEachColleague(
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );
        return data.stream().map(obj -> {
            Map<String, Object> map = new HashMap<>();
            map.put("colleague", obj[0]);
            map.put("date", obj[1].toString());
            map.put("appointments", obj[2]);
            return map;
        }).collect(Collectors.toList());
    }

    @PostMapping("/colleague-feedback")
    public ResponseEntity<Void> submitFeedback(@RequestBody ColleagueFeedback feedback) {
        colleagueService.saveFeedback(feedback);
        return ResponseEntity.ok().build();
    }


}
