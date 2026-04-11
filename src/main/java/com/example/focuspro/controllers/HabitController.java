package com.example.focuspro.controllers;

import com.example.focuspro.dtos.HabitDTO;
import com.example.focuspro.dtos.HabitLogRequest;
import com.example.focuspro.dtos.HabitRequest;
import com.example.focuspro.entities.Users;
import com.example.focuspro.services.HabitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/habits")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "http://https://focuspro-fm2d.onrender.com",
        "http://localhost:8080"
}, allowedHeaders = "*", methods = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.OPTIONS
})
public class HabitController {

    @Autowired
    private HabitService habitService;

    // GET /habits
    @GetMapping
    public List<HabitDTO> getHabits() {
        Users user = currentUser();
        return habitService.getHabits(user.getId());
    }

    // POST /habits
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HabitDTO createHabit(@RequestBody HabitRequest req) {
        Users user = currentUser();
        return habitService.createHabit(user.getId(), req);
    }

    // PUT /habits/{id}
    @PutMapping("/{id}")
    public HabitDTO updateHabit(@PathVariable int id, @RequestBody HabitRequest req) {
        Users user = currentUser();
        return habitService.updateHabit(user.getId(), id, req);
    }

    // DELETE /habits/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHabit(@PathVariable int id) {
        Users user = currentUser();
        habitService.deleteHabit(user.getId(), id);
    }

    // POST /habits/{id}/log — upserts today's habit_log row
    @PostMapping("/{id}/log")
    public HabitDTO logHabit(@PathVariable int id, @RequestBody HabitLogRequest req) {
        Users user = currentUser();
        return habitService.logHabit(user.getId(), id, req);
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
