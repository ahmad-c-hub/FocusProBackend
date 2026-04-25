package com.example.focuspro.services;

import com.example.focuspro.dtos.DailyGameLeaderboardDTO;
import com.example.focuspro.dtos.DailyGameScoreSubmitRequest;
import com.example.focuspro.dtos.DailyGameStatusDTO;
import com.example.focuspro.dtos.LeaderboardEntryDTO;
import com.example.focuspro.entities.DailyGameScore;
import com.example.focuspro.entities.GameResult;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.DailyGameScoreRepo;
import com.example.focuspro.repos.GameResultRepo;
import com.example.focuspro.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class DailyGameService {

    private static final String[] GAME_TYPES  = {"visual_nback", "go_no_go", "flanker_task"};
    private static final String[] GAME_TITLES = {"Visual N-Back", "Go/No-Go", "Flanker Task"};
    private static final String[] GAME_DESCS  = {
        "Based on Jaeggi et al. (2008, PNAS) — one of the only cognitive training tasks with evidence for fluid intelligence transfer. Measures working memory updating: your brain's ability to hold and continuously refresh information. Higher N-Back performance predicts academic and professional success in complex environments.",
        "Based on Aron & Poldrack (2005) and the Verbruggen & Logan (2008) Go/No-Go paradigm. Measures response inhibition — your brain's ability to suppress automatic actions. Commission errors (tapping when you should stop) directly measure impulse control. Deficits in this task are linked to ADHD, impulsivity traits, and poor self-regulation.",
        "Based on Eriksen & Eriksen (1974) — one of the most replicated paradigms in cognitive psychology. Measures selective attention and conflict monitoring: your ability to focus on relevant information and suppress distracting spatial input. The Flanker Effect (reaction time difference between congruent and incongruent trials) is a clinical measure used in ADHD diagnosis and executive function research."
    };

    @Autowired
    private DailyGameScoreRepo dailyGameScoreRepo;

    @Autowired
    private GameResultRepo gameResultRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private DailyScoreService dailyScoreService;

    private int todayDayIndex() {
        return (int) (LocalDate.now(ZoneOffset.UTC).toEpochDay() % 3);
    }

    public DailyGameStatusDTO getTodayStatus(Users currentUser) {
        int dayIndex    = todayDayIndex();
        String gameType = GAME_TYPES[dayIndex];
        String title    = GAME_TITLES[dayIndex];
        String desc     = GAME_DESCS[dayIndex];
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<DailyGameScore> scores = dailyGameScoreRepo.findByGameDateOrderByScoreDesc(today);
        int totalPlayers = scores.size();

        boolean hasPlayed = dailyGameScoreRepo.existsByUserIdAndGameDate(currentUser.getId(), today);
        Integer userScore = null;
        Integer userRank  = null;

        if (hasPlayed) {
            userScore = dailyGameScoreRepo.findByUserIdAndGameDate(currentUser.getId(), today)
                    .map(DailyGameScore::getScore)
                    .orElse(null);
            userRank = IntStream.range(0, scores.size())
                    .filter(i -> scores.get(i).getUserId() == currentUser.getId())
                    .map(i -> i + 1)
                    .boxed()
                    .findFirst()
                    .orElse(null);
        }

        return new DailyGameStatusDTO(gameType, title, desc, today, hasPlayed, userScore, userRank, totalPlayers);
    }

    public DailyGameStatusDTO submitDailyScore(DailyGameScoreSubmitRequest request, Users user) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        if (dailyGameScoreRepo.existsByUserIdAndGameDate(user.getId(), today)) {
            throw new RuntimeException("Already submitted today's score");
        }

        int dayIndex    = todayDayIndex();
        String gameType = GAME_TYPES[dayIndex];
        int score       = request.getScore();

        DailyGameScore daily = new DailyGameScore();
        daily.setUserId(user.getId());
        daily.setUsername(user.getUsername());
        daily.setDisplayName(user.getName() != null ? user.getName() : user.getUsername());
        daily.setGameType(gameType);
        daily.setScore(score);
        daily.setGameDate(today);
        dailyGameScoreRepo.save(daily);

        double focusScoreGained = calculateFocusScoreGained(gameType, score, request.isCompleted());
        GameResult result = new GameResult();
        result.setGameId(0);
        result.setUserId(user.getId());
        result.setScore(score);
        result.setTimePlayedSeconds(request.getTimePlayedSeconds());
        result.setCompleted(request.isCompleted());
        result.setPlayedAt(LocalDateTime.now());
        result.setFocusScoreGained(focusScoreGained);
        gameResultRepo.save(result);

        double newFocusScore = Math.min(100.0,
                (user.getFocusScore() != null ? user.getFocusScore() : 0.0) + focusScoreGained);
        user.setFocusScore(newFocusScore);
        userRepo.save(user);

        dailyScoreService.addPoints(user.getId(), focusScoreGained);

        activityLogService.log(
                user.getId(),
                "DAILY_GAME_PLAYED",
                "Played daily game: " + gameType + " — Score: " + score,
                "{\"gameType\":\"" + gameType + "\",\"score\":" + score + "}"
        );

        return getTodayStatus(user);
    }

    public DailyGameLeaderboardDTO getTodayLeaderboard(Users currentUser) {
        int dayIndex    = todayDayIndex();
        String gameType = GAME_TYPES[dayIndex];
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<DailyGameScore> scores = dailyGameScoreRepo.findByGameDateOrderByScoreDesc(today);
        List<LeaderboardEntryDTO> entries = IntStream.range(0, scores.size())
                .mapToObj(i -> {
                    DailyGameScore s = scores.get(i);
                    return new LeaderboardEntryDTO(
                            i + 1,
                            s.getDisplayName(),
                            s.getUsername(),
                            s.getScore(),
                            s.getUserId() == currentUser.getId()
                    );
                })
                .collect(Collectors.toList());

        LeaderboardEntryDTO currentUserEntry = entries.stream()
                .filter(LeaderboardEntryDTO::isCurrentUser)
                .findFirst()
                .orElse(null);

        return new DailyGameLeaderboardDTO(gameType, today, entries, currentUserEntry);
    }

    private double calculateFocusScoreGained(String gameType, int score, boolean completed) {
        return switch (gameType) {
            case "visual_nback" -> Math.min(5.0, score / 40.0);
            case "go_no_go"     -> Math.min(4.0, score / 60.0);
            case "flanker_task" -> completed
                    ? Math.min(5.0, score / 60.0)
                    : Math.min(2.0, score / 60.0);
            default -> 0.0;
        };
    }
}
