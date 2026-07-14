package ru.vtb.kamp.school.ecpemulator.web;

/** Прикладная ошибка ЕЦП: превращается в конверт с {@code error_code != 0} при HTTP 200. */
public class EcpError extends RuntimeException {

    private final int code;

    public EcpError(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static EcpError missingParam(String name) {
        return new EcpError(Envelope.MISSING_PARAM, "Не передан обязательный параметр: " + name);
    }

    public static EcpError badParam(String message) {
        return new EcpError(Envelope.BAD_PARAM, message);
    }

    public static EcpError business(String message) {
        return new EcpError(Envelope.BUSINESS_ERROR, message);
    }
}
