package com.example.focuspro.services;

import com.example.focuspro.dtos.FocusScheduleDTO;
import com.example.focuspro.dtos.FocusScheduleRequest;
import com.example.focuspro.dtos.LockInSessionDTO;
import com.example.focuspro.dtos.StartLockInRequest;
import com.example.focuspro.entities.FocusSchedule;
import com.example.focuspro.entities.LockInSession;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.FocusScheduleRepo;
import com.example.focuspro.repos.LockInSessionRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LockInService {

    @Autowired private LockInSessionRepo lockInSessionRepo;
    @Autowired private FocusScheduleRepo focusScheduleRepo;
    @Autowired private ActivityLogService activityLogService;

    // ── a) Start lock-in session ──────────────────────────────────────────────

    public LockInSessionDTO startLockIn(StartLockInRequest request) {
        Users user = currentUser();

        // Return existing active session — never create a duplicate
        Optional<LockInSession> existing = lockInSessionRepo.findByUserIdAndEndedAtIsNull(user.getId());
        if (existing.isPresent()) {
            return toDTO(existing.get());
        }

        LocalDateTime now = LocalDateTime.now();
        LockInSession session = new LockInSession();
        session.setUserId(user.getId());
        session.setScheduleId(request.getScheduleId());
        session.setSessionDate(LocalDate.now());
        session.setStartedAt(now);
        session.setPrepEndsAt(now.plusMinutes(request.getPrepTimerMinutes()));
        session.setScheduledEndsAt(now.plusMinutes(request.getDurationMinutes()));
        session.setEndedEarly(false);

        LockInSession saved = lockInSessionRepo.save(session);

        activityLogService.log(user.getId(), "LOCK_IN_STARTED",
                "Lock-in session started — prep " + request.getPrepTimerMinutes()
                        + "m, duration " + request.getDurationMinutes() + "m");

        return toDTO(saved);
    }

    // ── b) End lock-in session ────────────────────────────────────────────────

    public LockInSessionDTO endLockIn(Long sessionId, boolean endedEarly) {
        Users user = currentUser();

        LockInSession session = lockInSessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (session.getUserId() != user.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        session.setEndedAt(LocalDateTime.now());
        session.setEndedEarly(endedEarly);
        lockInSessionRepo.save(session);

        activityLogService.log(user.getId(), "LOCK_IN_ENDED",
                "Lock-in session " + (endedEarly ? "ended early" : "completed"));

        return toDTO(session);
    }

    // ── c) Get active session ─────────────────────────────────────────────────

    public LockInSessionDTO getActiveSession() {
        Users user = currentUser();
        return lockInSessionRepo.findByUserIdAndEndedAtIsNull(user.getId())
                .map(this::toDTO)
                .orElse(null);
    }

    // ── d) Create schedule ────────────────────────────────────────────────────

    public FocusScheduleDTO createSchedule(FocusScheduleRequest request) {
        Users user = currentUser();

        FocusSchedule schedule = new FocusSchedule();
        schedule.setUserId(user.getId());
        schedule.setScheduleType(request.getScheduleType());
        schedule.setScheduledTime(request.getScheduledTime());
        schedule.setDurationMinutes(request.getDurationMinutes());
        schedule.setPrepTimerMinutes(request.getPrepTimerMinutes());
        schedule.setRecurring(request.isRecurring());
        schedule.setDaysOfWeek(request.getDaysOfWeek());
        schedule.setActive(true);
        schedule.setCreatedAt(LocalDateTime.now());

        FocusSchedule saved = focusScheduleRepo.save(schedule);

        activityLogService.log(user.getId(), "FOCUS_SCHEDULE_CREATED",
                request.getScheduleType() + " schedule created for " + request.getScheduledTime());

        return toScheduleDTO(saved);
    }

    // ── e) Get all schedules ──────────────────────────────────────────────────

    public List<FocusScheduleDTO> getSchedules() {
        Users user = currentUser();
        return focusScheduleRepo.findByUserId(user.getId()).stream()
                .map(this::toScheduleDTO)
                .toList();
    }

    // ── f) Toggle schedule active state ──────────────────────────────────────

    public FocusScheduleDTO toggleSchedule(Long scheduleId) {
        Users user = currentUser();

        FocusSchedule schedule = focusScheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        if (schedule.getUserId() != user.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        schedule.setActive(!schedule.isActive());
        focusScheduleRepo.save(schedule);

        return toScheduleDTO(schedule);
    }

    // ── g) Delete schedule ────────────────────────────────────────────────────

    public void deleteSchedule(Long scheduleId) {
        Users user = currentUser();

        FocusSchedule schedule = focusScheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        if (schedule.getUserId() != user.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        focusScheduleRepo.delete(schedule);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LockInSessionDTO toDTO(LockInSession s) {
        return new LockInSessionDTO(
                s.getId(),
                s.getScheduleId(),
                s.getSessionDate(),
                s.getStartedAt(),
                s.getPrepEndsAt(),
                s.getScheduledEndsAt(),
                s.getEndedAt(),
                s.isEndedEarly(),
                s.getLinkedCoachingSessionId()
        );
    }

    private FocusScheduleDTO toScheduleDTO(FocusSchedule fs) {
        return new FocusScheduleDTO(
                fs.getId(),
                fs.getUserId(),
                fs.getScheduleType(),
                fs.getScheduledTime(),
                fs.getDurationMinutes(),
                fs.getPrepTimerMinutes(),
                fs.isRecurring(),
                fs.getDaysOfWeek(),
                fs.isActive(),
                fs.getCreatedAt(),
                fs.getLastTriggeredAt()
        );
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
