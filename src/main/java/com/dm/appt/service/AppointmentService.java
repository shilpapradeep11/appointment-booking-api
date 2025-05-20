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
            System.out.println("Loading .ONNX file from " + modelPath);

            Criteria<float[][], Classifications> criteria = Criteria.builder()
                    .setTypes(float[][].class, Classifications.class)
                    .optModelPath(modelPath)
                    .optEngine("OnnxRuntime")
                    .optProgress(new ProgressBar())
                    .optTranslator(new Translator<float[][], Classifications>() {
                        @Override
                        public NDList processInput(TranslatorContext ctx, float[][] input) {
                            NDManager manager = ctx.getNDManager();
                            NDArray array = manager.create(input);
                            return new NDList(array);
                        }

                        @Override
                        public Classifications processOutput(TranslatorContext ctx, NDList list) {
                            NDArray probabilities = list.singletonOrThrow();

                            // Assume two classes: 0 => low urgency, 1 => high urgency
                            List<String> classes = Arrays.asList("low", "high");
                            return new Classifications(classes, probabilities);
                        }

                        @Override
                        public Batchifier getBatchifier() {
                            return null; // No batching
                        }
                    })
                    .build();

            try (ZooModel<float[][], Classifications> model = ModelZoo.loadModel(criteria);
                 Predictor<float[][], Classifications> predictor = model.newPredictor()) {

                float[] features = new float[]{
                        Duration.between(LocalDateTime.now(), request.getRequestedDate()).toHours(),
                        "Video".equalsIgnoreCase(request.getInteractionMethod()) ? 1f : 0f,
                        "Telephone".equalsIgnoreCase(request.getInteractionMethod()) ? 1f : 0f
                };

                Classifications result = predictor.predict(new float[][]{features});
                float probability = (float) result.best().getProbability();
                System.out.println("[ML] Predicted urgency score: " + probability);
                return probability;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return 0.5f; // fallback
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
