package com.example.focuspro.services;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OtpStore {

    private static final long TTL_SECONDS = 600; // 10 minutes

    private record Entry(String otp, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> pending = new ConcurrentHashMap<>();
    private final Set<String> verified = ConcurrentHashMap.newKeySet();

    /** Store a fresh OTP for the given email (replaces any existing one). */
    public void store(String email, String otp) {
        purgeExpired();
        pending.put(normalise(email), new Entry(otp, Instant.now().plusSeconds(TTL_SECONDS)));
    }

    /**
     * Validate the OTP. On success the entry is consumed and the email is
     * added to the verified set so {@link #isVerified} returns true.
     */
    public boolean verify(String email, String otp) {
        purgeExpired();
        String key = normalise(email);
        Entry entry = pending.get(key);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            pending.remove(key);
            return false;
        }
        if (!entry.otp().equals(otp)) {
            return false;
        }
        pending.remove(key);
        verified.add(key);
        return true;
    }

    /** True if the email passed OTP verification and account creation hasn't happened yet. */
    public boolean isVerified(String email) {
        return verified.contains(normalise(email));
    }

    /** Called after a successful registration to clean up the verified set. */
    public void clearVerified(String email) {
        verified.remove(normalise(email));
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
