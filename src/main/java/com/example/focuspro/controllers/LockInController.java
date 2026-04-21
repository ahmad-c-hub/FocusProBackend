package com.example.focuspro.controllers;

import com.example.focuspro.dtos.FocusScheduleDTO;
import com.example.focuspro.dtos.FocusScheduleRequest;
import com.example.focuspro.dtos.LockInSessionDTO;
import com.example.focuspro.dtos.StartLockInRequest;
import com.example.focuspro.services.LockInService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/lockin")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com",
        "http://localhost:8080"
}, allowedHeaders = "*", methods = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.PATCH,
        RequestMethod.OPTIONS
})
public class LockInController {

    @Autowired
    private LockInService lockInService;

    // POST /lockin/start
    @PostMapping("/start")
    public ResponseEntity<LockInSessionDTO> startLockIn(@RequestBody StartLockInRequest request) {
        return ResponseEntity.ok(lockInService.startLockIn(request));
    }

    // POST /lockin/{id}/end?early=false
    @PostMapping("/{id}/end")
    public ResponseEntity<LockInSessionDTO> endLockIn(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "false") boolean early) {
        return ResponseEntity.ok(lockInService.endLockIn(id, early));
    }

    // GET /lockin/active
    @GetMapping("/active")
    public ResponseEntity<LockInSessionDTO> getActiveSession() {
        LockInSessionDTO session = lockInService.getActiveSession();
        if (session == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(session);
    }

    // POST /lockin/schedules
    @PostMapping("/schedules")
    public ResponseEntity<FocusScheduleDTO> createSchedule(@RequestBody FocusScheduleRequest request) {
        return ResponseEntity.ok(lockInService.createSchedule(request));
    }

    // GET /lockin/schedules
    @GetMapping("/schedules")
    public ResponseEntity<List<FocusScheduleDTO>> getSchedules() {
        return ResponseEntity.ok(lockInService.getSchedules());
    }

    // PATCH /lockin/schedules/{id}/toggle
    @PatchMapping("/schedules/{id}/toggle")
    public ResponseEntity<FocusScheduleDTO> toggleSchedule(@PathVariable Long id) {
        return ResponseEntity.ok(lockInService.toggleSchedule(id));
    }

    // DELETE /lockin/schedules/{id}
    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        lockInService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }
}
