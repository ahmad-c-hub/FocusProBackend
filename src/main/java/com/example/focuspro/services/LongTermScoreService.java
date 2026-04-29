package com.example.focuspro.services;

import com.example.focuspro.entities.Users;
import org.springframework.stereotype.Service;

/**
 * Long-Term Focus Score service.
 *
 * The long-term score is stored directly in users.long_term_score and is
 * updated once per night by FocusScoreScheduler using the formula below.
 * This class is NOT responsible for computing it on-the-fly — it only
 * reads the stored value and exposes the applyOneDay() formula the
 * scheduler calls.
 *
 * Formula applied each night before the daily score resets:
 *   normalized  = clamp(effectivePts / 50, 0, 1) × 100
 *   delta       = (normalized − 50) × 0.10
 *   adjustment  = delta ≥ 0 ? delta × (100 − lt) / 100   // headroom
 *                           : delta × lt / 100            // footroom
 *   new_lt      = clamp(lt + adjustment, 0, 100)
 *
 * Properties:
 *   • 50 effective pts/day = neutral (score doesn't change)
 *   • Score can never mathematically reach 0 or 100
 *   • Only changes at midnight — inactivity during the day has no effect
 */
@Service
public class LongTermScoreService {

    static final double PERFECT_DAY      = 50.0;
    static final double INFLUENCE_FACTOR = 0.10;

    /** Returned by compute(). */
    public record LongTermScoreData(Double score, Double weekTrend) {}

    /**
     * Returns the stored long-term score for the user.
     * Falls back to focus_score (diagnostic baseline) if long_term_score
     * hasn't been seeded yet (new user whose first midnight hasn't run).
     * Returns (null, null) if the user has no baseline at all.
     */
    public LongTermScoreData compute(Users user) {
        Double stored = user.getLongTermScore();
        if (stored == null || stored == 0.0) {
            stored = user.getFocusScore();
        }
        if (stored == null || stored == 0.0) {
            return new LongTermScoreData(null, null);
        }
        return new LongTermScoreData(stored, null);
    }

    /**
     * Applies one day's formula to the stored long-term score.
     * Called by FocusScoreScheduler at midnight before resetting daily_score.
     *
     * @param currentScore  stored long_term_score value  (0–100)
     * @param effectivePts  yesterday's effective daily points (≥ 0)
     * @return              new long_term_score, bounded in [0, 100]
     */
    public static double applyOneDay(double currentScore, double effectivePts) {
        double norm  = Math.min(effectivePts / PERFECT_DAY, 1.0) * 100.0;
        double delta = (norm - 50.0) * INFLUENCE_FACTOR;

        double adjustment = delta >= 0
                ? delta * (100.0 - currentScore) / 100.0
                : delta * currentScore / 100.0;

        return Math.max(0.0, Math.min(100.0, currentScore + adjustment));
    }
}
