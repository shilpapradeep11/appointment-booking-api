package com.dm.appt.impl;

import com.dm.appt.entity.Colleague;
import com.dm.appt.entity.ColleagueCalendar;
import com.dm.appt.repo.ColleagueCalendarRepository;
import com.dm.appt.repo.ColleagueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private ColleagueRepository colleagueRepository;

    @Autowired
    private ColleagueCalendarRepository calendarRepository;

    @Override
    public void run(String... args) {
        if (colleagueRepository.count() == 0) {
            List<String> skills = List.of("Banking", "Insurance", "Borrowing", "Mortgages", "Other services");
            Random rand = new Random();

            for (int i = 0; i < 5; i++) {
                Colleague c = new Colleague();
                c.setName("Colleague " + (i + 1));
                c.setAvailable(true);
                c.setSkill(skills.get(i));

                // ✅ Generate a random 4-digit job code (e.g., 1000–9999)
                String jobCode = String.format("%04d", rand.nextInt(9000) + 1000);
                c.setJobCode(jobCode);

                colleagueRepository.save(c);
            }
        }

        List<Colleague> colleagues = colleagueRepository.findAll();
        LocalDate today = LocalDate.now();

        for (Colleague c : colleagues) {
            for (int i = 0; i < 5; i++) { // next 5 days
                LocalDate date = today.plusDays(i);
                for (int hour = 9; hour < 17; hour++) {
                    boolean isBusy = Math.random() < 0.4; // 40% chance of being busy
                    if (isBusy) {
                        ColleagueCalendar entry = new ColleagueCalendar();
                        entry.setColleague(c);
                        entry.setDate(date);
                        entry.setStartTime(LocalTime.of(hour, 0));
                        entry.setEndTime(LocalTime.of(hour + 1, 0));
                        entry.setStatus("BUSY");
                        calendarRepository.save(entry);
                    }
                }
            }
        }
    }
}

