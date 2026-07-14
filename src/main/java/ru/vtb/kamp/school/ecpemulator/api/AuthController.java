package ru.vtb.kamp.school.ecpemulator.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vtb.kamp.school.ecpemulator.auth.SessionRegistry;
import ru.vtb.kamp.school.ecpemulator.web.Envelope;
import ru.vtb.kamp.school.ecpemulator.web.Params;

import java.util.List;

/**
 * Вендор-авторизация ЕЦП: {@code api/user/login?apiKey&login&password} → sess_id.
 * В openapi.yaml не входит (забота платформы), но нужна клиенту medicina-test (EcpClient).
 * Принимаются любые непустые логин/пароль. Метод GET — по IX.00-036; POST — на всякий случай.
 */
@RestController
public class AuthController {

    private final SessionRegistry sessions;

    public AuthController(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @RequestMapping(path = "/api/user/login", method = {RequestMethod.GET, RequestMethod.POST})
    public Envelope login(@RequestParam(required = false) String login,
                          @RequestParam(required = false) String password) {
        Params.require("login", login);
        Params.require("password", password);
        return Envelope.ok(List.of(new LoginResult(sessions.open())));
    }

    record LoginResult(@JsonProperty("sess_id") String sessId) {
    }
}
