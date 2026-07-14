package ru.vtb.kamp.school.ecpemulator.domain;

import org.springframework.stereotype.Component;
import ru.vtb.kamp.school.ecpemulator.config.EmuProps;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Booking;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Doctor;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Mo;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Patient;
import ru.vtb.kamp.school.ecpemulator.domain.Model.RefItem;
import ru.vtb.kamp.school.ecpemulator.domain.Model.Slot;
import ru.vtb.kamp.school.ecpemulator.web.EcpError;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory состояние эмулятора: сиды (те же id, что в mock-ecp — Lpu 13, специальности 3800/3815,
 * врачи 100/101/200, пациент 900, бирки с 5001) + stateful-логика брони/отмены.
 * Мутации — под монитором объекта: бронь конкурентна по определению.
 */
@Component
public class Store {

    private final List<Mo> mos = new ArrayList<>();
    private final List<Doctor> doctors = new ArrayList<>();
    private final List<Patient> patients = new ArrayList<>();
    private final Map<String, Slot> slotsById = new LinkedHashMap<>();
    /** key = Person_id + "|" + TimeTableGraf_id */
    private final Map<String, Booking> bookingsByKey = new LinkedHashMap<>();
    private final Map<String, List<RefItem>> refbooks = new LinkedHashMap<>();

    public Store(EmuProps props) {
        seed(props.scheduleDays());
    }

    private void seed(int scheduleDays) {
        mos.add(new Mo("13", "ГКБ №1", "Городская клиническая больница №1",
                "г. Пенза, ул. Ленина, 1", "г. Пенза, ул. Мира, 10"));

        doctors.add(new Doctor("100", "900", "Иванов", "Иван", "Иванович", "77", "13", "5", "3800"));
        doctors.add(new Doctor("101", "901", "Петрова", "Мария", "Сергеевна", "77", "13", "5", "3800"));
        doctors.add(new Doctor("200", "902", "Сидоров", "Пётр", "Николаевич", "88", "13", "7", "3815"));

        patients.add(new Patient("900", "Иванов", "Иван", "Иванович", LocalDate.of(1990, 5, 21),
                "1", "9161234567", "16364841483", "13", "9177", "123456789012"));
        patients.add(new Patient("903", "Смирнова", "Анна", "Петровна", LocalDate.of(1985, 11, 2),
                "2", "9169876543", "11223344595", "13", "9177", "210987654321"));

        refbooks.put("dbo.MedSpecOms", List.of(
                new RefItem("3800", "Терапия", "27"),
                new RefItem("3815", "Кардиология", "14")));
        refbooks.put("dbo.Post", List.of(
                new RefItem("77", "Врач-терапевт", "79"),
                new RefItem("88", "Врач-кардиолог", "90")));
        refbooks.put("dbo.LpuSectionProfile", List.of(
                new RefItem("5", "Терапевтический", "97"),
                new RefItem("7", "Кардиологический", "38")));
        refbooks.put("dbo.UslugaComplex", List.of(
                new RefItem("41001", "Приём врача-терапевта первичный", "B01.047.001"),
                new RefItem("41002", "Приём врача-кардиолога первичный", "B01.015.001")));
        refbooks.put("dbo.TimeTableType", List.of(
                new RefItem("1", "Обычная", "1"),
                new RefItem("2", "Диспансеризация", "2")));

        // Бирки: каждому врачу на scheduleDays дней вперёд, утро 09:00–11:20, шаг 20 минут.
        long nextId = 5001;
        LocalDate today = LocalDate.now();
        for (Doctor doctor : doctors) {
            for (int day = 0; day < scheduleDays; day++) {
                for (int n = 0; n < 8; n++) {
                    LocalDateTime beg = LocalDateTime.of(today.plusDays(day), LocalTime.of(9, 0).plusMinutes(20L * n));
                    String id = String.valueOf(nextId++);
                    slotsById.put(id, new Slot(id, doctor.medStaffFactId(), beg, 20));
                }
            }
        }
    }

    // --- Справочные выборки -------------------------------------------------

    public List<Mo> mos(String lpuId) {
        return mos.stream().filter(mo -> lpuId == null || mo.lpuId().equals(lpuId)).toList();
    }

    public Mo requireMo(String lpuId) {
        return mos.stream().filter(mo -> mo.lpuId().equals(lpuId)).findFirst()
                .orElseThrow(() -> EcpError.business("Медицинская организация не найдена: " + lpuId));
    }

    public List<Doctor> doctors(String lpuId, String medSpecOmsId, String postId) {
        return doctors.stream()
                .filter(d -> d.lpuId().equals(lpuId))
                .filter(d -> medSpecOmsId == null || d.medSpecOmsId().equals(medSpecOmsId))
                .filter(d -> postId == null || d.postId().equals(postId))
                .toList();
    }

    public Optional<Doctor> doctor(String medStaffFactId) {
        return doctors.stream().filter(d -> d.medStaffFactId().equals(medStaffFactId)).findFirst();
    }

    public Optional<Patient> patient(String personId) {
        return patients.stream().filter(p -> p.personId().equals(personId)).findFirst();
    }

    public List<Patient> patients() {
        return List.copyOf(patients);
    }

    public Optional<List<RefItem>> refbook(String code) {
        return Optional.ofNullable(refbooks.get(code));
    }

    public Optional<RefItem> refItem(String code, String id) {
        return refbook(code).stream().flatMap(List::stream).filter(i -> i.id().equals(id)).findFirst();
    }

    // --- Расписание ----------------------------------------------------------

    /** Свободные бирки врача, начиная с текущего момента. */
    public synchronized List<Slot> freeSlots(String medStaffFactId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        return slotsById.values().stream()
                .filter(s -> s.medStaffFactId().equals(medStaffFactId))
                .filter(s -> date == null || s.begTime().toLocalDate().equals(date))
                .filter(s -> s.begTime().isAfter(now))
                .filter(Slot::isFree)
                .toList();
    }

    public synchronized List<LocalDate> freeDates(String medStaffFactId, LocalDate beg, LocalDate end) {
        return freeSlots(medStaffFactId, null).stream()
                .map(s -> s.begTime().toLocalDate())
                .filter(d -> !d.isBefore(beg) && !d.isAfter(end))
                .distinct()
                .sorted()
                .toList();
    }

    /** Счётчик свободных бирок по врачам МО (для For_Record=1 и TimetableGraf_Count). */
    public synchronized int freeSlotCount(String medStaffFactId) {
        return freeSlots(medStaffFactId, null).size();
    }

    // --- Запись на приём -----------------------------------------------------

    /** Бронь бирки. Бизнес-ошибки — формулировками из IX.00-036. */
    public synchronized Slot book(String personId, String slotId) {
        if (patient(personId).isEmpty()) {
            throw EcpError.business("Пациент не найден в системе");
        }
        Slot slot = slotsById.get(slotId);
        if (slot == null || slot.begTime().isBefore(LocalDateTime.now())) {
            throw EcpError.business("Не найдена свободная бирка");
        }
        Booking existing = bookingsByKey.get(key(personId, slotId));
        if (existing != null && existing.isActive()) {
            throw EcpError.business("Человек уже записан на данную бирку");
        }
        if (!slot.isFree()) {
            throw EcpError.business("Не найдена свободная бирка");
        }
        slot.book(personId);
        bookingsByKey.put(key(personId, slotId), new Booking(personId, slotId));
        return slot;
    }

    /** Отмена записи: высвобождает бирку, статус пары становится 12. */
    public synchronized void cancel(String timeTableId) {
        Slot slot = slotsById.get(timeTableId);
        if (slot == null || slot.isFree()) {
            throw EcpError.business("Запись не найдена");
        }
        Booking booking = bookingsByKey.get(key(slot.bookedByPersonId(), timeTableId));
        if (booking != null) {
            booking.cancel();
        }
        slot.free();
    }

    public synchronized Optional<Booking> booking(String personId, String slotId) {
        return Optional.ofNullable(bookingsByKey.get(key(personId, slotId)));
    }

    /** Предстоящие (активные, в будущем) записи пациента. */
    public synchronized List<Slot> upcomingSlots(String personId) {
        LocalDateTime now = LocalDateTime.now();
        return bookingsByKey.values().stream()
                .filter(b -> b.personId().equals(personId) && b.isActive())
                .map(b -> slotsById.get(b.slotId()))
                .filter(s -> s != null && s.begTime().isAfter(now))
                .sorted((a, b) -> a.begTime().compareTo(b.begTime()))
                .toList();
    }

    public Optional<Slot> slot(String slotId) {
        return Optional.ofNullable(slotsById.get(slotId));
    }

    private static String key(String personId, String slotId) {
        return personId + "|" + slotId;
    }
}
