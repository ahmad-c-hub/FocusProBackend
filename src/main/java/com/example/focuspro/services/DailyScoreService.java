package com.example.focuspro.services;

import com.example.focuspro.entities.DailyScore;
import com.example.focuspro.repos.DailyScoreRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class DailyScoreService {

    @Autowired
    private DailyScoreRepo dailyScoreRepo;

    /**
     * Adds points to the user's accumulated daily score for today.
     * Returns the new total after adding.
     */
    public double addPoints(int userId, double points) {
        if (points <= 0) return getTodayScore(userId);

        LocalDate today = LocalDate.now();
        DailyScore entry = dailyScoreRepo.findByUserIdAndScoreDate(userId, today)
                .orElseGet(() -> {
                    DailyScore d = new DailyScore();
                    d.setUserId(userId);
                    d.setScoreDate(today);
                    d.setTotalPoints(0.0);
                    return d;
                });

        entry.setTotalPoints(entry.getTotalPoints() + points);
        dailyScoreRepo.save(entry);
        return entry.getTotalPoints();
    }

    /**
     * Returns today's effective daily score (raw points minus screen penalty, min 0).
     * Returns 0.0 if no record exists.
     */
    public double getTodayScore(int userId) {
        return dailyScoreRepo.findByUserIdAndScoreDate(userId, LocalDate.now())
                .map(DailyScore::getEffectivePoints)
                .orElse(0.0);
    }

    /**
     * Stores the screen-time penalty for the given user and date.
     * Called automatically after every screen-time sync; overwrites the previous penalty
     * so repeated syncs within the same day always reflect the latest usage totals.
     */
    public void applyScreenPenalty(int userId, LocalDate date, int penalty) {
        DailyScore entry = dailyScoreRepo.findByUserIdAndScoreDate(userId, date)
                .orElseGet(() -> {
                    DailyScore d = new DailyScore();
                    d.setUserId(userId);
                    d.setScoreDate(date);
                    d.setTotalPoints(0.0);
                    return d;
                });
        entry.setScreenPenalty(penalty);
        dailyScoreRepo.save(entry);
    }

    /**
     * Returns the last 7 days of daily scores (today inclusive),
     * with missing days filled in as 0.0.
     */
    public List<DailyScoreEntry> getWeeklyScores(int userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);

        List<DailyScore> records = dailyScoreRepo
                .findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(userId, weekAgo, today);

        List<DailyScoreEntry> result = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            LocalDate date = weekAgo.plusDays(i);
            double pts = records.stream()
                    .filter(r -> r.getScoreDate().equals(date))
                    .mapToDouble(DailyScore::getEffectivePoints)
                    .findFirst()
                    .orElse(0.0);
            result.add(new DailyScoreEntry(date.toString(), pts));
        }
        return result;
    }

    /** Simple DTO returned to the controller and serialised to JSON. */
    public record DailyScoreEntry(String date, double totalPoints) {}
}
