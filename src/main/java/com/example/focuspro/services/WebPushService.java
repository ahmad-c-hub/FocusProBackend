package com.example.focuspro.services;

import com.example.focuspro.entities.WebPushSubscription;
import com.example.focuspro.repos.WebPushSubscriptionRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.List;
import java.util.Map;

@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    @Autowired private WebPushSubscriptionRepo subscriptionRepo;

    @Value("${vapid.public.key:}")  private String vapidPublicKey;
    @Value("${vapid.private.key:}") private String vapidPrivateKey;
    @Value("${vapid.subject:mailto:admin@focuspro.com}") private String vapidSubject;

    private PushService pushService;
    private boolean enabled = false;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (vapidPublicKey.isBlank() || vapidPrivateKey.isBlank()) {
            log.warn("VAPID keys not set — web push disabled. Set VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY on Render.");
            return;
        }
        try {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            enabled = true;
            log.info("VAPID Web Push initialized.");
        } catch (Exception e) {
            log.error("Failed to initialize VAPID Web Push: {}", e.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }

    public String getVapidPublicKey() { return vapidPublicKey; }

    /** Save or update a browser push subscription for a user. */
    public void saveSubscription(int userId, String endpoint, String p256dh, String auth) {
        // Replace existing subscription with same endpoint to avoid duplicates
        subscriptionRepo.findByUserIdAndEndpoint(userId, endpoint).ifPresentOrElse(
            existing -> {
                existing.setP256dh(p256dh);
                existing.setAuth(auth);
                subscriptionRepo.save(existing);
            },
            () -> {
                WebPushSubscription sub = new WebPushSubscription();
                sub.setUserId(userId);
                sub.setEndpoint(endpoint);
                sub.setP256dh(p256dh);
                sub.setAuth(auth);
                subscriptionRepo.save(sub);
            }
        );
        log.info("Web push subscription saved for user {}", userId);
    }

    /**
     * Send a push notification to all of a user's browser subscriptions.
     * Returns true only if at least one subscription existed and was attempted.
     */
    public boolean sendToUser(int userId, String title, String body) {
        if (!enabled) return false;
        List<WebPushSubscription> subs = subscriptionRepo.findByUserId(userId);
        if (subs.isEmpty()) {
            log.warn("Web push: no subscriptions found for user {} — falling back to polling", userId);
            return false;
        }
        for (WebPushSubscription sub : subs) {
            sendToSubscription(sub, title, body);
        }
        return true;
    }

    private void sendToSubscription(WebPushSubscription sub, String title, String body) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(Map.of("title", title, "body", body));
            Notification notification = new Notification(
                    sub.getEndpoint(),
                    sub.getP256dh(),
                    sub.getAuth(),
                    payload
            );
            pushService.send(notification);
            log.info("Web push sent to user {} subscription", sub.getUserId());
        } catch (Exception e) {
            log.warn("Web push failed for subscription {} (endpoint may be expired): {}",
                    sub.getId(), e.getMessage());
            // If the subscription is gone (browser unsubscribed), clean it up
            if (e.getMessage() != null && (e.getMessage().contains("410") || e.getMessage().contains("404"))) {
                subscriptionRepo.deleteByUserIdAndEndpoint(sub.getUserId(), sub.getEndpoint());
                log.info("Removed expired web push subscription for user {}", sub.getUserId());
            }
        }
    }
}
