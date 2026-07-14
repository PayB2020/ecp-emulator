package ru.vtb.kamp.school.ecpemulator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Booking;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Doctor;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Slot;
import ru.vtb.kamp.school.ecpemulator.domain.Store;
import ru.vtb.kamp.school.ecpemulator.web.EcpError;
import ru.vtb.kamp.school.ecpemulator.web.Envelope;
import ru.vtb.kamp.school.ecpemulator.web.Params;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Домен «записи»: бронь, список предстоящих, статус, отмена. Параметры — в query (соглашение ЕЦП). */
@RestController
public class AppointmentController {

    private final Store store;

    public AppointmentController(Store store) {
        this.store = store;
    }

    /** Бронь бирки. Ключ записи — пара (Person_id, TimeTableGraf_id). */
    @PostMapping("/api/TimeTableGraf/TimeTableGrafWrite")
    public Envelope write(@RequestParam(name = "Person_id", required = false) String personId,
                          @RequestParam(name = "TimeTableGraf_id", required = false) String timeTableGrafId,
                          @RequestParam(name = "EvnQueue_id", required = false) String evnQueueId) {
        Params.require("Person_id", personId);
        Params.require("TimeTableGraf_id", timeTableGrafId);
        Slot slot = store.book(personId, timeTableGrafId);
        return Envelope.ok(List.of(new WriteResultDto(personId, slot.id())));
    }

    /** Предстоящие записи пациента (только будущие активные; история/отменённые не отдаются). */
    @GetMapping("/api/TimeTableListbyPatient")
    public Envelope listByPatient(@RequestParam(name = "Person_id", required = false) String personId,
                                  @RequestParam(name = "TimeTable_beg", required = false) String beg,
                                  @RequestParam(name = "TimeTable_end", required = false) String end) {
        Params.require("Person_id", personId);
        if (store.patient(personId).isEmpty()) {
            throw EcpError.business("Пациент не найден в системе");
        }
        LocalDateTime begDt = Params.optionalDateTime("TimeTable_beg", beg, false);
        LocalDateTime endDt = Params.optionalDateTime("TimeTable_end", end, true);
        List<TimeTableItemDto> visits = store.upcomingSlots(personId).stream()
                .filter(s -> begDt == null || !s.begTime().isBefore(begDt))
                .filter(s -> endDt == null || !s.begTime().isAfter(endDt))
                .map(this::toItem)
                .toList();
        return Envelope.ok(List.of(new PatientTimeTableDto(personId, visits)));
    }

    /** Статус записи по паре (Person_id, TimeTableGraf_id): 17 — записано, 12 — отменено. */
    @GetMapping("/api/TimeTableGraf/TimeTableGrafStatus")
    public Envelope status(@RequestParam(name = "Person_id", required = false) String personId,
                           @RequestParam(name = "TimeTableGraf_id", required = false) String timeTableGrafId) {
        Params.require("Person_id", personId);
        Params.require("TimeTableGraf_id", timeTableGrafId);
        List<StatusDto> data = store.booking(personId, timeTableGrafId)
                .map(b -> List.of(new StatusDto(b.statusId(), b.statusName())))
                .orElse(List.of());
        return Envelope.ok(data);
    }

    /** Отмена записи: высвобождение бирки. */
    @DeleteMapping("/api/TimeTable")
    public Envelope cancel(@RequestParam(name = "TimeTable_id", required = false) String timeTableId,
                           @RequestParam(name = "TimeTableSource", required = false) String source,
                           @RequestParam(name = "FailCause", required = false) Integer failCause) {
        Params.require("TimeTable_id", timeTableId);
        Params.require("TimeTableSource", source);
        if (!Set.of("Graf", "MedService").contains(source)) {
            throw EcpError.badParam("Недопустимое значение TimeTableSource: " + source + " (ожидается Graf|MedService)");
        }
        if (failCause == null) {
            throw EcpError.missingParam("FailCause");
        }
        store.cancel(timeTableId);
        return Envelope.ok(List.of());
    }

    private TimeTableItemDto toItem(Slot slot) {
        Doctor doctor = store.doctor(slot.medStaffFactId()).orElseThrow();
        String postName = store.refItem("dbo.Post", doctor.postId())
                .map(i -> i.name()).orElse(null);
        return new TimeTableItemDto(slot.id(), slot.begTime().format(Params.DT), null,
                doctor.lpuId(), doctor.medStaffFactId(), postName, null, null, "Graf");
    }

    record WriteResultDto(@JsonProperty("Person_id") String personId,
                          @JsonProperty("TimeTableGraf_id") String timeTableGrafId) {
    }

    record PatientTimeTableDto(@JsonProperty("Person_id") String personId,
                               @JsonProperty("TimeTable") List<TimeTableItemDto> timeTable) {
    }

    record TimeTableItemDto(@JsonProperty("TimeTable_id") String timeTableId,
                            @JsonProperty("TimeTable_begTime") String begTime,
                            @JsonProperty("TimeTable_factTime") String factTime,
                            @JsonProperty("Lpu_id") String lpuId,
                            @JsonProperty("MedStaffFact_id") String medStaffFactId,
                            @JsonProperty("Post_Name") String postName,
                            @JsonProperty("MedService_Name") String medServiceName,
                            @JsonProperty("Usluga_Name") String uslugaName,
                            @JsonProperty("TimeTableSource") String timeTableSource) {
    }

    record StatusDto(@JsonProperty("EvnStatus_id") String evnStatusId,
                     @JsonProperty("EvnStatus_Name") String evnStatusName) {
    }
}
