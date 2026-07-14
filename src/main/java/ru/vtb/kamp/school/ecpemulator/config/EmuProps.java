package ru.vtb.kamp.school.ecpemulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки эмулятора.
 *
 * @param apiKey            ожидаемый apiKey; пустая строка — принимается любой непустой
 * @param requireSession    требовать sess_id на прикладных методах (error_code 1/2 по конверту)
 * @param sessionTtlMinutes время жизни сессии, минут
 * @param scheduleDays      горизонт генерации расписания, дней от сегодня
 */
@ConfigurationProperties(prefix = "ecp")
public record EmuProps(
        @DefaultValue("") String apiKey,
        @DefaultValue("false") boolean requireSession,
        @DefaultValue("30") int sessionTtlMinutes,
        @DefaultValue("14") int scheduleDays) {
}
