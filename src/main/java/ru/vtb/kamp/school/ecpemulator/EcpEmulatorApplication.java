package ru.vtb.kamp.school.ecpemulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Эмулятор партнёрского API ЕЦП/ПроМед (РТ МИС) по контракту ecp-api/openapi.yaml.
 * В отличие от WireMock-мока — stateful: бронь занимает бирку, отмена высвобождает,
 * список записей и статус отражают реальное состояние.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EcpEmulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcpEmulatorApplication.class, args);
    }
}
