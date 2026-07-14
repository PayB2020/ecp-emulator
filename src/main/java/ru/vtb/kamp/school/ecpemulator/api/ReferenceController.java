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

import java.util.List;

/** Справочники (Refbook), МО и карточка врача. */
@RestController
public class ReferenceController {

    private final Store store;

    public ReferenceController(Store store) {
        this.store = store;
    }

    /** Элементы справочника по строковому коду (dbo.*); без id — весь справочник. */
    @GetMapping("/api/Refbook")
    public Envelope refbook(@RequestParam(name = "Refbook_Code", required = false) String code,
                            @RequestParam(name = "id", required = false) String id) {
        Params.require("Refbook_Code", code);
        List<RefbookItemDto> data = store.refbook(code)
                .orElseThrow(() -> EcpError.badParam("Неизвестный справочник: " + code))
                .stream()
                .filter(item -> id == null || item.id().equals(id))
                .map(item -> new RefbookItemDto(item.id(), item.name(), item.code(), "2020-01-01", null))
                .toList();
        return Envelope.ok(data);
    }

    /** Медицинские организации: без Lpu_id — все. */
    @GetMapping("/api/MO")
    public Envelope mo(@RequestParam(name = "Lpu_id", required = false) String lpuId) {
        List<MoDto> data = store.mos(lpuId).stream()
                .map(mo -> new MoDto(mo.lpuId(), mo.nick(), mo.name(), mo.uAddress(), mo.pAddress()))
                .toList();
        return Envelope.ok(data);
    }

    /** Карточка врача: ФИО, специальность, должность. */
    @GetMapping("/api/rish/MedStaffFact/getDoctorInfo")
    public Envelope doctorInfo(@RequestParam(name = "MedStaffFact_id", required = false) String medStaffFactId) {
        Params.require("MedStaffFact_id", medStaffFactId);
        Doctor doctor = store.doctor(medStaffFactId)
                .orElseThrow(() -> EcpError.business("Врач не найден: " + medStaffFactId));
        String specName = store.refItem("dbo.MedSpecOms", doctor.medSpecOmsId()).map(i -> i.name()).orElse(null);
        String postName = store.refItem("dbo.Post", doctor.postId()).map(i -> i.name()).orElse(null);
        return Envelope.ok(List.of(new DoctorInfoDto(doctor.fio(), specName, postName)));
    }

    record RefbookItemDto(@JsonProperty("id") String id,
                          @JsonProperty("Name") String name,
                          @JsonProperty("Code") String code,
                          @JsonProperty("BegDate") String begDate,
                          @JsonProperty("EndDate") String endDate) {
    }

    record MoDto(@JsonProperty("Lpu_id") String lpuId,
                 @JsonProperty("Lpu_Nick") String nick,
                 @JsonProperty("Lpu_Name") String name,
                 @JsonProperty("UAddress_Address") String uAddress,
                 @JsonProperty("PAddress_Address") String pAddress) {
    }

    record DoctorInfoDto(@JsonProperty("Fio") String fio,
                         @JsonProperty("MedSpecOms_Name") String medSpecOmsName,
                         @JsonProperty("Dolgnost_Name") String dolgnostName) {
    }
}
