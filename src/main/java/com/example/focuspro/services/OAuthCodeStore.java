package com.example.focuspro.services;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores short-lived one-time codes that map to JWT tokens.
 * The code is placed in the OAuth redirect URL instead of the token itself,
 * so the real JWT never appears in browser history or server logs.
 */
@Component
public class OAuthCodeStore {

    private static final long TTL_SECONDS = 60;

    private record Entry(String jwt, Instant expiresAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** Store a JWT and return a new one-time code. */
    public String store(String jwt) {
        purgeExpired();
        String code = UUID.randomUUID().toString();
        store.put(code, new Entry(jwt, Instant.now().plusSeconds(TTL_SECONDS)));
        return code;
    }

    /**
     * Exchange a code for its JWT.
     * Returns null if the code is unknown or expired.
     * The code is consumed (deleted) on first use.
     */
    public String exchange(String code) {
        purgeExpired();
        Entry entry = store.remove(code);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return null;
        }
        return entry.jwt();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}
