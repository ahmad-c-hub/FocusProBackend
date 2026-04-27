package com.example.focuspro.services;

import com.example.focuspro.entities.DailyScore;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.DailyScoreRepo;
import com.example.focuspro.repos.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Runs at midnight every day and applies yesterday's daily score to each
 * user's long-term focus score using the proportional headroom/footroom
 * delta formula defined in {@link LongTermScoreService#applyOneDay}.
 *
 * Formula recap:
 *   normalized  = clamp(effectivePts / 50, 0, 1) × 100
 *   delta       = (normalized − 50) × 0.10
 *   adjustment  = delta ≥ 0 ? delta × (100 − lt) / 100    // headroom
 *                           : delta × lt / 100             // footroom
 *   new_lt      = lt + adjustment                          // stays in [0, 100]
 *
 * Rules:
 *   • Users who haven't completed the diagnostic (focusScore = 0 or null)
 *     are skipped — their long-term score hasn't been seeded yet.
 *   • If a user had no daily_score record for yesterday (inactive day), their
 *     effective points are treated as 0, which produces a small proportional
 *     decay (not a flat penalty).
 *   • The diagnostic seed score written at diagnostic completion is never
 *     overwritten by this job — only scores > 0 are updated.
 */
@Component
public class FocusScoreScheduler {

    private static final Logger log = LoggerFactory.getLogger(FocusScoreScheduler.class);

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private DailyScoreRepo dailyScoreRepo;

    /**
     * Triggered at 00:00:00 server time every day.
     * Reads yesterday's effective daily score for every active user and
     * applies the proportional delta formula to their stored focusScore.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void applyNightlyScoreUpdate() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("[FocusScoreScheduler] Running nightly update for date={}", yesterday);

        List<Users> users = userRepo.findAll();
        int updated = 0;
        int skipped = 0;

        for (Users user : users) {
            Double current = user.getFocusScore();

            // Skip users who haven't completed the diagnostic yet
            if (current == null || current == 0.0) {
                skipped++;
                continue;
            }

            // Fetch yesterday's effective score (raw points minus screen penalty)
            double effectivePts = dailyScoreRepo
                    .findByUserIdAndScoreDate(user.getId(), yesterday)
                    .map(DailyScore::getEffectivePoints)
                    .orElse(0.0); // inactive day → 0 pts → small decay

            double newScore = LongTermScoreService.applyOneDay(current, effectivePts);

            user.setFocusScore(newScore);
            userRepo.save(user);
            updated++;

            log.debug("[FocusScoreScheduler] user={} pts={} {} → {}",
                    user.getId(), effectivePts,
                    String.format("%.2f", current),
                    String.format("%.2f", newScore));
        }

        log.info("[FocusScoreScheduler] Done. updated={} skipped={}", updated, skipped);
    }
}
