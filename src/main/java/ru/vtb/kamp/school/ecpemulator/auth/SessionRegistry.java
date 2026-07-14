package ru.vtb.kamp.school.ecpemulator.auth;

import org.springframework.stereotype.Component;
import ru.vtb.kamp.school.ecpemulator.config.EmuProps;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Сессии ЕЦП: login → sess_id, TTL из настроек, продление при каждом использовании. */
@Component
public class SessionRegistry {

    private final Map<String, Instant> expiryBySessId = new ConcurrentHashMap<>();
    private final Duration ttl;

    public SessionRegistry(EmuProps props) {
        this.ttl = Duration.ofMinutes(props.sessionTtlMinutes());
    }

    public String open() {
        String sessId = UUID.randomUUID().toString();
        expiryBySessId.put(sessId, Instant.now().plus(ttl));
        return sessId;
    }

    /** Действительна ли сессия; действующая — продлевается. */
    public boolean isActive(String sessId) {
        if (sessId == null) {
            return false;
        }
        Instant expiry = expiryBySessId.get(sessId);
        if (expiry == null || expiry.isBefore(Instant.now())) {
            expiryBySessId.remove(sessId);
            return false;
        }
        expiryBySessId.put(sessId, Instant.now().plus(ttl));
        return true;
    }
}
