package com.example.focuspro.controllers;

import com.example.focuspro.dtos.ActivityLogDTO;
import com.example.focuspro.entities.Users;
import com.example.focuspro.services.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/activity")
@CrossOrigin(
        origins = {
                "http://localhost:3000",
                "http://10.0.2.2:8080",
                "http://localhost:5000"
        },
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.OPTIONS}
)
public class ActivityLogController {

    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping("/logs")
    public List<ActivityLogDTO> getLogs() {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return activityLogService.getUserLogs(user.getId());
    }
}
