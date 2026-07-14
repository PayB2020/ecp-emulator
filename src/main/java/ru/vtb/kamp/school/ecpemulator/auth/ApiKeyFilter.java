package ru.vtb.kamp.school.ecpemulator.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.vtb.kamp.school.ecpemulator.config.EmuProps;
import ru.vtb.kamp.school.ecpemulator.web.Envelope;

import java.io.IOException;

/**
 * Авторизация по соглашениям ЕЦП: apiKey обязателен на всех /api/** — иначе HTTP 401 без конверта.
 * Сессия (при ecp.require-session=true) проверяется на прикладных методах: отсутствие sess_id —
 * конверт error_code=1, неизвестный/просроченный — error_code=2 (HTTP 200).
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/user/login";

    private final EmuProps props;
    private final SessionRegistry sessions;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(EmuProps props, SessionRegistry sessions, ObjectMapper objectMapper) {
        this.props = props;
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getParameter("apiKey");
        boolean keyOk = apiKey != null && !apiKey.isBlank()
                && (props.apiKey().isBlank() || props.apiKey().equals(apiKey));
        if (!keyOk) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (props.requireSession() && !LOGIN_PATH.equals(request.getRequestURI())) {
            String sessId = request.getParameter("sess_id");
            if (sessId == null || sessId.isBlank()) {
                writeEnvelope(response, Envelope.error(Envelope.NO_SESSION, "Не передан идентификатор сессии"));
                return;
            }
            if (!sessions.isActive(sessId)) {
                writeEnvelope(response, Envelope.error(Envelope.SESSION_EXPIRED, "Сессия не найдена или просрочена"));
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void writeEnvelope(HttpServletResponse response, Envelope envelope) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), envelope);
    }
}
