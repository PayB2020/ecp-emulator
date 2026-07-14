package ru.vtb.kamp.school.ecpemulator.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Доменные сущности эмулятора (внутреннее состояние, не DTO ответов). */
public final class Model {

    private Model() {
    }

    public record Mo(String lpuId, String nick, String name, String uAddress, String pAddress) {
    }

    /** Место работы врача (MedStaffFact) со связкой на специальность/должность/отделение. */
    public record Doctor(String medStaffFactId, String personId, String surName, String firName, String secName,
                         String postId, String lpuId, String lpuSectionId, String medSpecOmsId) {

        public String fio() {
            return surName + " " + firName + " " + secName;
        }
    }

    public record Patient(String personId, String surName, String firName, String secName, LocalDate birthDay,
                          String sexId, String phone, String snils, String lpuId,
                          String polisSer, String polisNum) {
    }

    /** Элемент справочника api/Refbook. */
    public record RefItem(String id, String name, String code) {
    }

    /** Бирка (слот) расписания. {@code TimeTable_id == TimeTableGraf_id} в рамках эмулятора. */
    public static final class Slot {
        private final String id;
        private final String medStaffFactId;
        private final LocalDateTime begTime;
        private final int durationMin;
        private String bookedByPersonId;

        public Slot(String id, String medStaffFactId, LocalDateTime begTime, int durationMin) {
            this.id = id;
            this.medStaffFactId = medStaffFactId;
            this.begTime = begTime;
            this.durationMin = durationMin;
        }

        public String id() {
            return id;
        }

        public String medStaffFactId() {
            return medStaffFactId;
        }

        public LocalDateTime begTime() {
            return begTime;
        }

        public int durationMin() {
            return durationMin;
        }

        public String bookedByPersonId() {
            return bookedByPersonId;
        }

        public void book(String personId) {
            this.bookedByPersonId = personId;
        }

        public void free() {
            this.bookedByPersonId = null;
        }

        public boolean isFree() {
            return bookedByPersonId == null;
        }
    }

    /**
     * Запись на приём: ключ — пара (Person_id, TimeTableGraf_id).
     * Статусы EvnStatus: 17 — записано, 12 — отменено.
     */
    public static final class Booking {
        public static final String STATUS_BOOKED = "17";
        public static final String STATUS_CANCELLED = "12";

        private final String personId;
        private final String slotId;
        private String statusId = STATUS_BOOKED;

        public Booking(String personId, String slotId) {
            this.personId = personId;
            this.slotId = slotId;
        }

        public String personId() {
            return personId;
        }

        public String slotId() {
            return slotId;
        }

        public String statusId() {
            return statusId;
        }

        public String statusName() {
            return STATUS_BOOKED.equals(statusId) ? "Записано" : "Отменено";
        }

        public boolean isActive() {
            return STATUS_BOOKED.equals(statusId);
        }

        public void cancel() {
            this.statusId = STATUS_CANCELLED;
        }
    }
}
