package ru.vtb.kamp.school.ecpemulator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Doctor;
import ru.vtb.kamp.school.ecpemulator.domain.Store;
import ru.vtb.kamp.school.ecpemulator.web.EcpError;
import ru.vtb.kamp.school.ecpemulator.web.Envelope;
import ru.vtb.kamp.school.ecpemulator.web.Params;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Домен «врачи»: специальности, врачи, свободные даты и бирки. */
@RestController
public class DoctorController {

    private final Store store;

    public DoctorController(Store store) {
        this.store = store;
    }

    /** Специальности, доступные для записи в МО; For_Record=1 добавляет счётчик свободных бирок. */
    @GetMapping("/api/MedSpecOms/MedSpecOmsByMO")
    public Envelope specialties(@RequestParam(name = "Lpu_id", required = false) String lpuId,
                                @RequestParam(name = "For_Record", required = false) Integer forRecord) {
        Params.require("Lpu_id", lpuId);
        store.requireMo(lpuId);
        boolean withCount = forRecord != null && forRecord == 1;
        List<MedSpecOmsDto> data = store.refbook("dbo.MedSpecOms").orElseThrow().stream()
                .filter(spec -> !store.doctors(lpuId, spec.id(), null).isEmpty())
                .map(spec -> new MedSpecOmsDto(spec.id(), spec.name(),
                        withCount ? store.doctors(lpuId, spec.id(), null).stream()
                                .mapToInt(d -> store.freeSlotCount(d.medStaffFactId())).sum() : null))
                .toList();
        return Envelope.ok(data);
    }

    /** Врачи (места работы) в МО, опционально по специальности и должности. */
    @GetMapping("/api/MedStaffFact/MedStaffFactByMO")
    public Envelope doctors(@RequestParam(name = "Lpu_id", required = false) String lpuId,
                            @RequestParam(name = "MedSpecOms_id", required = false) String medSpecOmsId,
                            @RequestParam(name = "Post_id", required = false) String postId) {
        Params.require("Lpu_id", lpuId);
        List<MedStaffFactDto> data = store.doctors(lpuId, medSpecOmsId, postId).stream()
                .map(d -> new MedStaffFactDto(d.medStaffFactId(), d.personId(), d.surName(), d.firName(),
                        d.secName(), d.postId(), d.lpuId(), d.lpuSectionId(),
                        store.freeSlotCount(d.medStaffFactId())))
                .toList();
        return Envelope.ok(data);
    }

    /** Свободные даты приёма врача в окне [beg, end]. */
    @GetMapping("/api/TimeTableGraf/TimeTableGrafFreeDate")
    public Envelope freeDates(@RequestParam(name = "MedStaffFact_id", required = false) String medStaffFactId,
                              @RequestParam(name = "TimeTableGraf_beg", required = false) String beg,
                              @RequestParam(name = "TimeTableGraf_end", required = false) String end) {
        Params.require("MedStaffFact_id", medStaffFactId);
        requireDoctor(medStaffFactId);
        LocalDate begDate = Params.requireDate("TimeTableGraf_beg", beg);
        LocalDate endDate = Params.requireDate("TimeTableGraf_end", end);
        List<FreeDateDto> data = store.freeDates(medStaffFactId, begDate, endDate).stream()
                .map(d -> new FreeDateDto(d.format(DateTimeFormatter.ISO_LOCAL_DATE)))
                .toList();
        return Envelope.ok(data);
    }

    /** Свободные бирки врача на дату. */
    @GetMapping("/api/TimeTableGraf/TimeTableGrafFreeTime")
    public Envelope freeTime(@RequestParam(name = "MedStaffFact_id", required = false) String medStaffFactId,
                             @RequestParam(name = "TimeTableGraf_begTime", required = false) String begTime,
                             @RequestParam(name = "Person_id", required = false) String personId) {
        Params.require("MedStaffFact_id", medStaffFactId);
        requireDoctor(medStaffFactId);
        LocalDate date = Params.requireDate("TimeTableGraf_begTime", begTime);
        List<FreeTimeDto> data = store.freeSlots(medStaffFactId, date).stream()
                .map(s -> new FreeTimeDto(s.id(), s.begTime().format(Params.DT), s.durationMin()))
                .toList();
        return Envelope.ok(data);
    }

    private Doctor requireDoctor(String medStaffFactId) {
        return store.doctor(medStaffFactId)
                .orElseThrow(() -> EcpError.business("Врач не найден: " + medStaffFactId));
    }

    record MedSpecOmsDto(@JsonProperty("MedSpecOms_id") String medSpecOmsId,
                         @JsonProperty("MedSpecClass_Name") String medSpecClassName,
                         @JsonProperty("TimetableGraf_Count") Integer timetableGrafCount) {
    }

    record MedStaffFactDto(@JsonProperty("MedStaffFact_id") String medStaffFactId,
                           @JsonProperty("Person_id") String personId,
                           @JsonProperty("PersonSurName_SurName") String surName,
                           @JsonProperty("PersonFirName_FirName") String firName,
                           @JsonProperty("PersonSecName_SecName") String secName,
                           @JsonProperty("Post_id") String postId,
                           @JsonProperty("Lpu_id") String lpuId,
                           @JsonProperty("LpuSection_id") String lpuSectionId,
                           @JsonProperty("TimetableGraf_Count") Integer timetableGrafCount) {
    }

    record FreeDateDto(@JsonProperty("TimeTableGraf_begTime") String begTime) {
    }

    record FreeTimeDto(@JsonProperty("TimeTableGraf_id") String timeTableGrafId,
                       @JsonProperty("TimeTableGraf_begTime") String begTime,
                       @JsonProperty("TimeTableGraf_Time") Integer durationMin) {
    }
}
