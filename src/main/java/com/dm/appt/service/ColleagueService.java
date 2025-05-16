package com.dm.appt.service;

import com.dm.appt.dto.ColleagueFeedback;
import com.dm.appt.entity.AppointmentRequest;
import com.dm.appt.entity.Colleague;
import com.dm.appt.repo.AppointmentRepository;
import com.dm.appt.repo.ColleagueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ColleagueService {
    @Autowired
    AppointmentRepository appointmentRepository;

    @Autowired
    ColleagueRepository colleagueRepository;
    public void saveFeedback(ColleagueFeedback feedback) {
        Optional<Colleague> optional = colleagueRepository.findById(feedback.getColleagueId());
        if (optional.isPresent()) {
            Colleague colleague = optional.get();
            colleague.setLastRating(feedback.getRating());
            colleague.setLastComment(feedback.getComment());
            colleagueRepository.save(colleague);
        }
    }


}
