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
 * Computes the Long-Term EMA Focus Score for a user.
 *
 * Formula (active day):   score = 0.15 × normalizedDaily + 0.85 × prevScore
 *   where normalizedDaily = clamp(dailyPts / 50, 0, 1) × 100
 * Formula (inactive day): score = max(0, prevScore − 0.5)
 *
 * The seed is the user's diagnostic focusScore.
 * All daily_score records since LOOKBACK_DAYS are used for the EMA walk.
 */
@Service
public class LongTermScoreService {

    private static final double ALPHA          = 0.15;
    private static final double PERFECT_DAY    = 50.0;
    private static final double INACTIVE_DECAY = 0.5;
    private static final int    LOOKBACK_DAYS  = 365;

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

        // EMA up to yesterday → current long-term score
        double currentScore = computeEma(seed, startDate, yesterday, scoreMap);

        // EMA up to 7 days ago → score a week ago (for trend)
        LocalDate weekAgoDate    = yesterday.minusDays(6);
        double    scoreWeekAgo   = computeEma(seed, startDate, weekAgoDate, scoreMap);
        double    weekTrend      = currentScore - scoreWeekAgo;

        return new LongTermScoreData(currentScore, weekTrend);
    }

    /**
     * Walks EMA from [from] to [to] inclusive, using [seed] as the starting value.
     * Days absent from scoreMap are treated as 0 points (inactive → decay).
     */
    private double computeEma(double seed, LocalDate from, LocalDate to,
                               Map<LocalDate, Double> scoreMap) {
        if (to.isBefore(from)) return seed;
        double score  = seed;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            double pts = scoreMap.getOrDefault(cursor, 0.0);
            if (pts > 0) {
                double norm = Math.min(pts / PERFECT_DAY, 1.0) * 100.0;
                score = ALPHA * norm + (1 - ALPHA) * score;
            } else {
                score = Math.max(0, score - INACTIVE_DECAY);
            }
            cursor = cursor.plusDays(1);
        }
        return Math.max(0, Math.min(100, score));
    }
}
