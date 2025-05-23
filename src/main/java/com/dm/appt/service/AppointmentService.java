package com.dm.appt.service;

import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Batchifier;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import com.dm.appt.dto.AppointmentWithColleague;
import com.dm.appt.entity.AppointmentRequest;
import com.dm.appt.entity.Colleague;
import com.dm.appt.repo.AppointmentRepository;
import com.dm.appt.repo.ColleagueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AppointmentService {
    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ColleagueRepository colleagueRepository;

    private AppointmentRequest appointmentRequest;

    public boolean allocateAutomatically(AppointmentRequest request) {
        long hoursUntil = Duration.between(LocalDateTime.now(), request.requestedDate).toHours();
        if (hoursUntil > 24) {
            return false;
        }

        List<Colleague> colleagues = colleagueRepository.findAll();

        for (Colleague colleague : colleagues) {
            List<AppointmentRequest> bookings = appointmentRepository.findByAllocatedTo(colleague.name);

            boolean hasConflict = bookings.stream().anyMatch(a -> {
                if (a.requestedDate == null) return false;
                long diffMinutes = Math.abs(Duration.between(a.requestedDate, request.requestedDate).toMinutes());
                return diffMinutes < 60;
            });

            if (!hasConflict) {
                colleague.available = false;
                colleagueRepository.save(colleague);
                request.allocatedTo = colleague.name;
                System.out.println("[AUTO] Appointment for " + request.applicationType + " allocated to: " + colleague.name);
                return true;
            }
        }

        System.out.println("[AUTO] No colleagues available for requested time slot.");
        return false;
    }


    public boolean isColleagueAvailableAt(Colleague colleague, LocalDateTime requestedDate) {
        if (colleague == null) return false;

        List<AppointmentRequest> overlappingAppointments =
                appointmentRepository.findByColleagueAndRequestedDate(colleague, requestedDate);

        return overlappingAppointments.isEmpty();
    }

    public Page<AppointmentWithColleague> getManualReviewAppointments(Pageable pageable) {

        List<AppointmentRequest> all = appointmentRepository.findWithColleagueByStatus("Manual Review Notified, manually allocated");

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<AppointmentRequest> paged = all.subList(start, end);


        List<AppointmentWithColleague> dtos =  paged.stream()
                .map(appointment -> {
                    Colleague colleague = appointment.getColleague();

                    // Defensive check
                    boolean isAvailable = false;

                    return new AppointmentWithColleague(
                            appointment.getId(),
                            appointment.getApplicationType(),
                            appointment.getInteractionMethod(),
                            appointment.getRequestedDate(),
                            appointment.getStatus(),
                    colleague != null ? colleague.getName() : "Unassigned",
                            isAvailable
                    );
                })
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, all.size());

    }

    public float predictUrgency(AppointmentRequest request) {
        try {
            Path modelPath = Paths.get("/models/urgency_predictor.onnx");
            System.out.println("✅ Loading .ONNX file from: " + modelPath);
            System.out.println("📁 Exists? " + Files.exists(modelPath));

            Criteria<NDList, NDList> criteria = Criteria.builder()
                    .setTypes(NDList.class, NDList.class)
                    .optModelPath(modelPath)
                    .optEngine("OnnxRuntime")
                    .optProgress(new ProgressBar())
                    .build();

            try (ZooModel<NDList, NDList> model = ModelZoo.loadModel(criteria);
                 Predictor<NDList, NDList> predictor = model.newPredictor()) {

                NDManager manager = NDManager.newBaseManager();

                // Convert your inputs to a float tensor with shape [1, 3]
                float[] features = new float[]{
                        Duration.between(LocalDateTime.now(), request.getRequestedDate()).toHours(),
                        "Video".equalsIgnoreCase(request.getInteractionMethod()) ? 1f : 0f,
                        "Telephone".equalsIgnoreCase(request.getInteractionMethod()) ? 1f : 0f
                };

                NDArray inputArray = manager.create(features, new Shape(1, 3));
                NDList input = new NDList(inputArray);

                NDList output = predictor.predict(input);
                long rawPrediction = output.get(0).toLongArray()[0]; // Correct method for int64
                float prediction = (float) rawPrediction;

                System.out.println("[DEBUG] Output NDArray: " + output.get(0));
                System.out.println("[DEBUG] Shape: " + output.get(0).getShape());
                System.out.println("[ML] Urgency prediction: " + prediction);
                return prediction;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return 0.5f; // fallback score
        }
    }



    public boolean checkIfAnyColleagueAvailableWithin24Hrs(AppointmentRequest req) {
        if (req.getRequestedDate() == null) return false;
        LocalDateTime now = LocalDateTime.now();
        if (req.getRequestedDate().isAfter(now.plusHours(24))) return true;

        List<Colleague> colleagues = colleagueRepository.findAll();
        for (Colleague c : colleagues) {
            boolean hasConflict = appointmentRepository.hasConflict(c,
                    req.getRequestedDate().minusMinutes(30),
                    req.getRequestedDate().plusMinutes(30));
            if (!hasConflict) return true;
        }
        return false;
    }

}
