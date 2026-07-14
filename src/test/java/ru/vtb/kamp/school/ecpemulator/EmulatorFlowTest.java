package ru.vtb.kamp.school.ecpemulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Полный флоу записи по контракту ecp-api/openapi.yaml: авторизация → специальности → врачи →
 * свободные даты/бирки → бронь → список/статус → отмена → бирка снова свободна. Плюс ошибки:
 * 401 без apiKey, error_code=3 без обязательного параметра, бизнес-ошибки брони.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmulatorFlowTest {

    private static final String API_KEY = "test-key";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    @Order(1)
    void withoutApiKey_http401() throws Exception {
        mvc.perform(get("/api/MO")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void login_returnsSessId() throws Exception {
        JsonNode resp = call(get("/api/user/login").queryParam("login", "vendor").queryParam("password", "x"));
        assertThat(resp.get("error_code").asInt()).isZero();
        assertThat(resp.get("data").get(0).get("sess_id").asText()).isNotBlank();
    }

    @Test
    @Order(3)
    void missingRequiredParam_errorCode3() throws Exception {
        JsonNode resp = call(get("/api/MedSpecOms/MedSpecOmsByMO"));
        assertThat(resp.get("error_code").asInt()).isEqualTo(3);
        assertThat(resp.get("error_msg").asText()).contains("Lpu_id");
    }

    @Test
    @Order(4)
    void fullBookingFlow() throws Exception {
        // Специальности МО с счётчиком бирок
        JsonNode specs = call(get("/api/MedSpecOms/MedSpecOmsByMO")
                .queryParam("Lpu_id", "13").queryParam("For_Record", "1"));
        assertThat(specs.get("error_code").asInt()).isZero();
        assertThat(specs.get("data")).anyMatch(s -> "3800".equals(s.get("MedSpecOms_id").asText()));
        assertThat(specs.get("data").get(0).get("TimetableGraf_Count").asInt()).isPositive();

        // Врачи по специальности
        JsonNode doctors = call(get("/api/MedStaffFact/MedStaffFactByMO")
                .queryParam("Lpu_id", "13").queryParam("MedSpecOms_id", "3800"));
        assertThat(doctors.get("data")).hasSize(2);
        String medStaffFactId = doctors.get("data").get(0).get("MedStaffFact_id").asText();

        // Свободные даты в окне 14 дней
        String beg = LocalDate.now().toString();
        String end = LocalDate.now().plusDays(13).toString();
        JsonNode dates = call(get("/api/TimeTableGraf/TimeTableGrafFreeDate")
                .queryParam("MedStaffFact_id", medStaffFactId)
                .queryParam("TimeTableGraf_beg", beg).queryParam("TimeTableGraf_end", end));
        assertThat(dates.get("data").size()).isPositive();
        String date = dates.get("data").get(0).get("TimeTableGraf_begTime").asText();

        // Свободные бирки на дату
        JsonNode slots = call(get("/api/TimeTableGraf/TimeTableGrafFreeTime")
                .queryParam("MedStaffFact_id", medStaffFactId).queryParam("TimeTableGraf_begTime", date));
        assertThat(slots.get("data").size()).isPositive();
        String slotId = slots.get("data").get(0).get("TimeTableGraf_id").asText();

        // Бронь
        JsonNode booked = call(post("/api/TimeTableGraf/TimeTableGrafWrite")
                .queryParam("Person_id", "900").queryParam("TimeTableGraf_id", slotId));
        assertThat(booked.get("error_code").asInt()).isZero();
        assertThat(booked.get("data").get(0).get("TimeTableGraf_id").asText()).isEqualTo(slotId);

        // Повторная бронь той же бирки тем же пациентом — бизнес-ошибка
        JsonNode again = call(post("/api/TimeTableGraf/TimeTableGrafWrite")
                .queryParam("Person_id", "900").queryParam("TimeTableGraf_id", slotId));
        assertThat(again.get("error_code").asInt()).isNotZero();
        assertThat(again.get("error_msg").asText()).contains("уже записан");

        // Чужим пациентом — бирка занята
        JsonNode occupied = call(post("/api/TimeTableGraf/TimeTableGrafWrite")
                .queryParam("Person_id", "903").queryParam("TimeTableGraf_id", slotId));
        assertThat(occupied.get("error_msg").asText()).contains("Не найдена свободная бирка");

        // Несуществующий пациент
        JsonNode noPerson = call(post("/api/TimeTableGraf/TimeTableGrafWrite")
                .queryParam("Person_id", "999").queryParam("TimeTableGraf_id", slotId));
        assertThat(noPerson.get("error_msg").asText()).contains("Пациент не найден");

        // Список предстоящих записей содержит бирку
        JsonNode list = call(get("/api/TimeTableListbyPatient").queryParam("Person_id", "900"));
        JsonNode visits = list.get("data").get(0).get("TimeTable");
        assertThat(visits).anyMatch(v -> slotId.equals(v.get("TimeTable_id").asText()));

        // Статус — 17 (записано)
        JsonNode st = call(get("/api/TimeTableGraf/TimeTableGrafStatus")
                .queryParam("Person_id", "900").queryParam("TimeTableGraf_id", slotId));
        assertThat(st.get("data").get(0).get("EvnStatus_id").asText()).isEqualTo("17");

        // Отмена
        JsonNode cancel = call(delete("/api/TimeTable")
                .queryParam("TimeTable_id", slotId).queryParam("TimeTableSource", "Graf")
                .queryParam("FailCause", "3"));
        assertThat(cancel.get("error_code").asInt()).isZero();

        // Статус — 12 (отменено), бирка снова в свободных
        JsonNode st2 = call(get("/api/TimeTableGraf/TimeTableGrafStatus")
                .queryParam("Person_id", "900").queryParam("TimeTableGraf_id", slotId));
        assertThat(st2.get("data").get(0).get("EvnStatus_id").asText()).isEqualTo("12");

        JsonNode slots2 = call(get("/api/TimeTableGraf/TimeTableGrafFreeTime")
                .queryParam("MedStaffFact_id", medStaffFactId).queryParam("TimeTableGraf_begTime", date));
        assertThat(slots2.get("data")).anyMatch(s -> slotId.equals(s.get("TimeTableGraf_id").asText()));
    }

    @Test
    @Order(5)
    void personSearch_byPolisAndByFio() throws Exception {
        JsonNode byPolis = call(get("/api/Person")
                .queryParam("Polis_Ser", "9177").queryParam("Polis_Num", "123456789012"));
        assertThat(byPolis.get("data").get(0).get("Person_id").asText()).isEqualTo("900");
        // Полис в ответе не возвращается
        assertThat(byPolis.get("data").get(0).has("Polis_Ser")).isFalse();

        JsonNode byFio = call(get("/api/Person")
                .queryParam("PersonSurName_SurName", "Смирнова")
                .queryParam("PersonFirName_FirName", "Анна")
                .queryParam("PersonBirthDay_BirthDay", "1985-11-02"));
        assertThat(byFio.get("data").get(0).get("Person_id").asText()).isEqualTo("903");

        // ЕНП: поиск по одному номеру, без серии
        JsonNode byNum = call(get("/api/Person").queryParam("Polis_Num", "210987654321"));
        assertThat(byNum.get("data").get(0).get("Person_id").asText()).isEqualTo("903");

        // Demo-фолбэк: неизвестный полис резолвится в первого пациента (совместимость с mock-ecp)
        JsonNode fallback = call(get("/api/Person").queryParam("Polis_Num", "0000000000000000"));
        assertThat(fallback.get("data").get(0).get("Person_id").asText()).isEqualTo("900");

        JsonNode onlySer = call(get("/api/Person").queryParam("Polis_Ser", "9177"));
        assertThat(onlySer.get("error_code").asInt()).isEqualTo(4);
    }

    @Test
    @Order(6)
    void referenceEndpoints() throws Exception {
        JsonNode attach = call(get("/api/PersonMainAttach").queryParam("Person_id", "900"));
        assertThat(attach.get("data").get(0).get("Lpu_id").asText()).isEqualTo("13");
        assertThat(attach.get("data").get(0).get("MedStaffFact_Fio").asText()).isNotBlank();

        JsonNode refbook = call(get("/api/Refbook").queryParam("Refbook_Code", "dbo.Post").queryParam("id", "77"));
        assertThat(refbook.get("data").get(0).get("Name").asText()).isEqualTo("Врач-терапевт");

        JsonNode unknown = call(get("/api/Refbook").queryParam("Refbook_Code", "dbo.Nope"));
        assertThat(unknown.get("error_code").asInt()).isEqualTo(4);

        JsonNode mo = call(get("/api/MO").queryParam("Lpu_id", "13"));
        assertThat(mo.get("data").get(0).get("Lpu_Nick").asText()).isEqualTo("ГКБ №1");

        JsonNode info = call(get("/api/rish/MedStaffFact/getDoctorInfo").queryParam("MedStaffFact_id", "100"));
        assertThat(info.get("data").get(0).get("Fio").asText()).isEqualTo("Иванов Иван Иванович");
        assertThat(info.get("data").get(0).get("MedSpecOms_Name").asText()).isEqualTo("Терапия");
    }

    @Test
    @Order(7)
    void swaggerUiAndContractAreServedWithoutApiKey() throws Exception {
        mvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request.queryParam("apiKey", API_KEY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }
}
