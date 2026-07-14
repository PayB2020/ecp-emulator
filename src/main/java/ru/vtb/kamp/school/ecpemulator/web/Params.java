package ru.vtb.kamp.school.ecpemulator.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Разбор query-параметров ЕЦП: обязательность → error_code 3, формат → error_code 4. */
public final class Params {

    /** Формат дат-времени ЕЦП: {@code ГГГГ-ММ-ДД чч:мм:сс}, без таймзоны. */
    public static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Params() {
    }

    public static String require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw EcpError.missingParam(name);
        }
        return value;
    }

    public static LocalDate requireDate(String name, String value) {
        require(name, value);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw EcpError.badParam("Неверный формат даты в параметре " + name + " (ожидается ГГГГ-ММ-ДД): " + value);
        }
    }

    /** Дата-время; принимает и {@code ГГГГ-ММ-ДД}, и {@code ГГГГ-ММ-ДД чч:мм:сс}. Null — если параметр не задан. */
    public static LocalDateTime optionalDateTime(String name, String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DT);
        } catch (DateTimeParseException ignored) {
            // пробуем как дату
        }
        try {
            LocalDate d = LocalDate.parse(value);
            return endOfDay ? d.atTime(23, 59, 59) : d.atStartOfDay();
        } catch (DateTimeParseException e) {
            throw EcpError.badParam("Неверный формат даты-времени в параметре " + name + ": " + value);
        }
    }
}
