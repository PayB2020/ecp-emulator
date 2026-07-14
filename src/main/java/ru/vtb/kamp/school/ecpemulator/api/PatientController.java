package ru.vtb.kamp.school.ecpemulator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Doctor;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Mo;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Patient;
import ru.vtb.kamp.school.ecpemulator.domain.Store;
import ru.vtb.kamp.school.ecpemulator.web.EcpError;
import ru.vtb.kamp.school.ecpemulator.web.Envelope;
import ru.vtb.kamp.school.ecpemulator.web.Params;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Домен «пациент»: поиск/профиль и основное прикрепление. */
@RestController
public class PatientController {

    private final Store store;

    public PatientController(Store store) {
        this.store = store;
    }

    /**
     * Поиск пациента: по Person_id, СНИЛС, полису ОМС (серия+номер двумя полями) или ФИО+дате рождения.
     * Полис в ответе не возвращается. Без единого критерия — error_code=4.
     */
    @GetMapping("/api/Person")
    public Envelope person(@RequestParam(name = "Person_id", required = false) String personId,
                           @RequestParam(name = "PersonSurName_SurName", required = false) String surName,
                           @RequestParam(name = "PersonFirName_FirName", required = false) String firName,
                           @RequestParam(name = "PersonSecName_SecName", required = false) String secName,
                           @RequestParam(name = "PersonBirthDay_BirthDay", required = false) String birthDay,
                           @RequestParam(name = "PersonSnils_Snils", required = false) String snils,
                           @RequestParam(name = "Polis_Ser", required = false) String polisSer,
                           @RequestParam(name = "Polis_Num", required = false) String polisNum,
                           @RequestParam(name = "Lpu_id", required = false) String lpuId) {
        boolean anyCriterion = personId != null || snils != null || polisSer != null || polisNum != null
                || surName != null || firName != null || birthDay != null;
        if (!anyCriterion) {
            throw EcpError.badParam("Не заданы параметры поиска пациента");
        }
        if ((polisSer == null) != (polisNum == null)) {
            throw EcpError.badParam("Для поиска по полису ОМС нужны оба поля: Polis_Ser и Polis_Num");
        }
        LocalDate birth = birthDay == null ? null : Params.requireDate("PersonBirthDay_BirthDay", birthDay);

        List<PersonDto> data = store.patients().stream()
                .filter(p -> personId == null || p.personId().equals(personId))
                .filter(p -> snils == null || snils.equals(p.snils()))
                .filter(p -> polisSer == null || (polisSer.equals(p.polisSer()) && polisNum.equals(p.polisNum())))
                .filter(p -> surName == null || p.surName().equalsIgnoreCase(surName))
                .filter(p -> firName == null || p.firName().equalsIgnoreCase(firName))
                .filter(p -> secName == null || p.secName().equalsIgnoreCase(secName))
                .filter(p -> birth == null || p.birthDay().equals(birth))
                .filter(p -> lpuId == null || lpuId.equals(p.lpuId()))
                .map(PatientController::toDto)
                .toList();
        return Envelope.ok(data);
    }

    /** Основное прикрепление: МО (id+название+адрес) + участковый врач. */
    @GetMapping("/api/PersonMainAttach")
    public Envelope mainAttach(@RequestParam(name = "Person_id", required = false) String personId) {
        Params.require("Person_id", personId);
        Patient patient = store.patient(personId)
                .orElseThrow(() -> EcpError.business("Пациент не найден в системе"));
        Mo mo = store.requireMo(patient.lpuId());
        Doctor precinct = store.doctors(patient.lpuId(), null, null).stream().findFirst().orElse(null);
        return Envelope.ok(List.of(new AttachDto(mo.lpuId(), mo.name(), mo.pAddress(),
                precinct == null ? null : precinct.medStaffFactId(),
                precinct == null ? null : precinct.fio())));
    }

    private static PersonDto toDto(Patient p) {
        return new PersonDto(p.personId(), p.surName(), p.firName(), p.secName(),
                p.birthDay().format(DateTimeFormatter.ISO_LOCAL_DATE), p.sexId(), p.phone(), p.snils(), p.lpuId());
    }

    record PersonDto(@JsonProperty("Person_id") String personId,
                     @JsonProperty("PersonSurName_SurName") String surName,
                     @JsonProperty("PersonFirName_FirName") String firName,
                     @JsonProperty("PersonSecName_SecName") String secName,
                     @JsonProperty("PersonBirthDay_BirthDay") String birthDay,
                     @JsonProperty("Person_Sex_id") String sexId,
                     @JsonProperty("PersonPhone_Phone") String phone,
                     @JsonProperty("PersonSnils_Snils") String snils,
                     @JsonProperty("Lpu_id") String lpuId) {
    }

    record AttachDto(@JsonProperty("Lpu_id") String lpuId,
                     @JsonProperty("Lpu_Name") String lpuName,
                     @JsonProperty("PAddress_Address") String pAddress,
                     @JsonProperty("MedStaffFact_id") String medStaffFactId,
                     @JsonProperty("MedStaffFact_Fio") String medStaffFactFio) {
    }
}
