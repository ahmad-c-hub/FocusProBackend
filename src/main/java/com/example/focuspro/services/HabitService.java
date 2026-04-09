package com.example.focuspro.services;

import com.example.focuspro.dtos.HabitDTO;
import com.example.focuspro.dtos.HabitLogRequest;
import com.example.focuspro.dtos.HabitRequest;
import com.example.focuspro.entities.Habit;
import com.example.focuspro.entities.HabitLog;
import com.example.focuspro.repos.HabitLogRepo;
import com.example.focuspro.repos.HabitRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class HabitService {

    @Autowired
    private HabitRepo habitRepo;

    @Autowired
    private HabitLogRepo habitLogRepo;

    @Autowired
    private ActivityLogService activityLogService;

    // ── GET all habits for a user ─────────────────────────────────────────────
    public List<HabitDTO> getHabits(int userId) {
        return habitRepo.findByUserId(userId)
                .stream()
                .map(h -> toDTO(h, userId))
                .toList();
    }

    // ── CREATE ────────────────────────────────────────────────────────────────
    public HabitDTO createHabit(int userId, HabitRequest req) {
        Habit habit = new Habit();
        habit.setUserId(userId);
        applyRequest(habit, req);
        habit = habitRepo.save(habit);
        activityLogService.log(userId, "HABIT_CREATED",
                "Created habit: " + habit.getTitle(),
                String.format("{\"habitId\":%d,\"title\":\"%s\"}", habit.getId(), habit.getTitle()));
        return toDTO(habit, userId);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────
    public HabitDTO updateHabit(int userId, int habitId, HabitRequest req) {
        Habit habit = habitRepo.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Habit not found"));
        applyRequest(habit, req);
        habit = habitRepo.save(habit);
        activityLogService.log(userId, "HABIT_UPDATED",
                "Updated habit: " + habit.getTitle(),
                String.format("{\"habitId\":%d,\"title\":\"%s\"}", habit.getId(), habit.getTitle()));
        return toDTO(habit, userId);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    public void deleteHabit(int userId, int habitId) {
        Habit habit = habitRepo.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Habit not found"));
        String title = habit.getTitle();
        habitRepo.delete(habit);
        activityLogService.log(userId, "HABIT_DELETED",
                "Deleted habit: " + title,
                String.format("{\"habitId\":%d,\"title\":\"%s\"}", habitId, title));
    }

    // ── LOG (upsert today's habit_log) ────────────────────────────────────────
    public HabitDTO logHabit(int userId, int habitId, HabitLogRequest req) {
        Habit habit = habitRepo.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Habit not found"));

        LocalDate today = LocalDate.now();

        // Upsert: update existing log or create new one
        HabitLog log = habitLogRepo
                .findByHabitIdAndLoggedDate(habitId, today)
                .orElseGet(() -> {
                    HabitLog newLog = new HabitLog();
                    newLog.setHabitId(habitId);
                    newLog.setUserId(userId);
                    newLog.setLoggedDate(today);
                    return newLog;
                });

        log.setCompleted(req.isCompleted());
        if (req.getTimeSpentMinutes() != null) {
            log.setTimeSpentMinutes(req.getTimeSpentMinutes());
        }
        habitLogRepo.save(log);

        if (req.isCompleted()) {
            activityLogService.log(userId, "HABIT_COMPLETED",
                    "Completed habit: " + habit.getTitle(),
                    String.format("{\"habitId\":%d,\"title\":\"%s\",\"date\":\"%s\"}",
                            habitId, habit.getTitle(), today));
        }

        return toDTO(habit, userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyRequest(Habit habit, HabitRequest req) {
        habit.setTitle(req.getTitle());
        habit.setDescription(req.getDescription());
        habit.setDurationMinutes(req.getDurationMinutes() > 0 ? req.getDurationMinutes() : 10);
        habit.setFrequencyPerWeek(req.getFrequencyPerWeek() > 0 ? req.getFrequencyPerWeek() : 1);
        habit.setMonday(req.isMonday());
        habit.setTuesday(req.isTuesday());
        habit.setWednesday(req.isWednesday());
        habit.setThursday(req.isThursday());
        habit.setFriday(req.isFriday());
        habit.setSaturday(req.isSaturday());
        habit.setSunday(req.isSunday());
    }

    private HabitDTO toDTO(Habit habit, int userId) {
        LocalDate today = LocalDate.now();

        boolean doneToday = habitLogRepo
                .findByHabitIdAndLoggedDate(habit.getId(), today)
                .map(HabitLog::isCompleted)
                .orElse(false);

        int streak = computeStreak(habit.getId());

        HabitDTO dto = new HabitDTO();
        dto.setId(habit.getId());
        dto.setTitle(habit.getTitle());
        dto.setDescription(habit.getDescription());
        dto.setDurationMinutes(habit.getDurationMinutes());
        dto.setFrequencyPerWeek(habit.getFrequencyPerWeek());
        dto.setMonday(habit.isMonday());
        dto.setTuesday(habit.isTuesday());
        dto.setWednesday(habit.isWednesday());
        dto.setThursday(habit.isThursday());
        dto.setFriday(habit.isFriday());
        dto.setSaturday(habit.isSaturday());
        dto.setSunday(habit.isSunday());
        dto.setDoneToday(doneToday);
        dto.setStreak(streak);
        dto.setCreatedAt(habit.getCreatedAt());
        dto.setUpdatedAt(habit.getUpdatedAt());
        return dto;
    }

    /**
     * Counts consecutive completed days ending today or yesterday.
     * Uses habit_logs ordered desc by logged_date.
     */
    private int computeStreak(int habitId) {
        List<LocalDate> completedDates = habitLogRepo
                .findByHabitIdAndCompletedTrueOrderByLoggedDateDesc(habitId)
                .stream()
                .map(HabitLog::getLoggedDate)
                .toList();

        if (completedDates.isEmpty()) return 0;

        LocalDate today = LocalDate.now();
        LocalDate mostRecent = completedDates.get(0);

        // Streak is broken if the most recent completion is older than yesterday
        if (mostRecent.isBefore(today.minusDays(1))) return 0;

        int streak = 0;
        LocalDate expected = mostRecent;
        for (LocalDate date : completedDates) {
            if (date.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }
}
