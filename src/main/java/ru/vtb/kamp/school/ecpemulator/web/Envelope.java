package ru.vtb.kamp.school.ecpemulator.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Конверт ответа ЕЦП: HTTP всегда 200, признак ошибки — {@code error_code != 0}.
 * Коды: 0 — успех, 1 — нет сессии, 2 — сессия просрочена, 3 — нет обязательного параметра,
 * 4 — ошибка параметров; бизнес-ошибки — {@link #BUSINESS_ERROR} с текстом в {@code error_msg}.
 */
public record Envelope(
        @JsonProperty("error_code") int errorCode,
        @JsonProperty("error_msg") String errorMsg,
        @JsonProperty("count") Integer count,
        @JsonProperty("offset") Integer offset,
        @JsonProperty("data") List<?> data) {

    public static final int NO_SESSION = 1;
    public static final int SESSION_EXPIRED = 2;
    public static final int MISSING_PARAM = 3;
    public static final int BAD_PARAM = 4;
    public static final int BUSINESS_ERROR = 6;

    public static Envelope ok(List<?> data) {
        return new Envelope(0, null, data.size(), 0, data);
    }

    public static Envelope error(int code, String msg) {
        return new Envelope(code, msg, null, null, List.of());
    }
}
