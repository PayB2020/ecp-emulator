package ru.vtb.kamp.school.ecpemulator.web;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Прикладные ошибки — всегда HTTP 200 с конвертом (соглашение ЕЦП). */
@RestControllerAdvice
public class EnvelopeExceptionHandler {

    @ExceptionHandler(EcpError.class)
    public Envelope ecpError(EcpError e) {
        return Envelope.error(e.code(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Envelope typeMismatch(MethodArgumentTypeMismatchException e) {
        return Envelope.error(Envelope.BAD_PARAM, "Неверное значение параметра: " + e.getName());
    }
}
