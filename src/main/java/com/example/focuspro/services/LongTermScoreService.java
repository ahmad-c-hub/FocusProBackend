package com.example.focuspro.services;

import com.example.focuspro.entities.DailyScore;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.DailyScoreRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes the Long-Term Focus Score for a user using a proportional
 * headroom/footroom delta formula.
 *
 * Formula (any day):
 *   normalized  = clamp(effectivePts / PERFECT_DAY, 0, 1) × 100
 *   delta       = (normalized − 50) × INFLUENCE_FACTOR
 *   adjustment  = delta ≥ 0  ?  delta × (100 − score) / 100   // headroom
 *                            :  delta × score / 100            // footroom
 *   new_score   = score + adjustment
 *
 * Key properties:
 *   • 50 normalized points is the true neutral point — score never changes.
 *   • The closer the score is to 100 the harder it is to gain (headroom shrinks).
 *   • The closer the score is to 0 the harder it is to lose (footroom shrinks).
 *   • The formula is self-bounding: mathematically never crosses 0 or 100.
 *   • Inactive days (0 pts) produce a small proportional decay, not a flat penalty.
 *
 * The seed is the user's diagnostic focusScore.
 * All daily_score records since LOOKBACK_DAYS are used for the historical walk.
 *
 * applyOneDay() is also exposed as a static helper so FocusScoreScheduler can
 * apply a single day's formula incrementally at midnight without a full history walk.
 */
@Service
public class LongTermScoreService {

    // 50 raw effective points = "perfect" day (normalized to 100).
    static final double PERFECT_DAY      = 50.0;
    // Controls the maximum daily swing: at ±50 delta, max adjustment is ±5 pts
    // (before headroom/footroom scaling).  Raise to 0.15 for faster changes,
    // lower to 0.05 for a more stubborn score.
    static final double INFLUENCE_FACTOR = 0.10;
    private static final int LOOKBACK_DAYS = 365;

    @Autowired
    private DailyScoreRepo dailyScoreRepo;

    /** Returned by compute(). */
    public record LongTermScoreData(Double score, Double weekTrend) {}

    /**
     * Computes the current long-term EMA score and 7-day trend for the given user.
     * Returns (null, null) if the user hasn't completed the diagnostic yet.
     */
    public LongTermScoreData compute(Users user) {
        Double seed = user.getFocusScore();
        if (seed == null || seed == 0.0) {
            return new LongTermScoreData(null, null);
        }

        LocalDate yesterday  = LocalDate.now().minusDays(1);
        LocalDate startDate  = LocalDate.now().minusDays(LOOKBACK_DAYS);

        // Fetch all daily scores in the window, build a date → points map
        List<DailyScore> records = dailyScoreRepo
                .findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        user.getId(), startDate, yesterday);

        // Use effective points (raw minus screen penalty) so heavy phone usage
        // naturally lowers the long-term EMA score.
        Map<LocalDate, Double> scoreMap = records.stream()
                .collect(Collectors.toMap(DailyScore::getScoreDate, DailyScore::getEffectivePoints));

        // Walk up to yesterday → current long-term score
        double currentScore = computeWalk(seed, startDate, yesterday, scoreMap);

        // Walk up to 7 days ago → score a week ago (for trend)
        LocalDate weekAgoDate  = yesterday.minusDays(6);
        double    scoreWeekAgo = computeWalk(seed, startDate, weekAgoDate, scoreMap);
        double    weekTrend    = currentScore - scoreWeekAgo;

        return new LongTermScoreData(currentScore, weekTrend);
    }

    /**
     * Walks the score from [from] to [to] inclusive, using [seed] as the starting value.
     * Days absent from scoreMap are treated as 0 effective points (inactive day).
     * Uses the proportional headroom/footroom delta formula on every day.
     */
    private double computeWalk(double seed, LocalDate from, LocalDate to,
                                Map<LocalDate, Double> scoreMap) {
        if (to.isBefore(from)) return seed;
        double score = seed;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            double pts = scoreMap.getOrDefault(cursor, 0.0);
            score = applyOneDay(score, pts);
            cursor = cursor.plusDays(1);
        }
        return score;
    }

    /**
     * Applies one day's worth of the proportional delta formula to [currentScore].
     *
     * @param currentScore  the long-term score before this day  (0–100)
     * @param effectivePts  the user's effective daily points for the day (≥ 0)
     * @return              the updated long-term score, naturally bounded in [0, 100]
     */
    public static double applyOneDay(double currentScore, double effectivePts) {
        double norm  = Math.min(effectivePts / PERFECT_DAY, 1.0) * 100.0;
        double delta = (norm - 50.0) * INFLUENCE_FACTOR;

        double adjustment = delta >= 0
                ? delta * (100.0 - currentScore) / 100.0   // headroom
                : delta * currentScore / 100.0;             // footroom

        // Self-bounding: can never cross 0 or 100, but clamp for safety
        return Math.max(0.0, Math.min(100.0, currentScore + adjustment));
    }
}
