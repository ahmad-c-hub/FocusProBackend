package com.example.focuspro.controllers;

import com.example.focuspro.dtos.GameLevelProgressDTO;
import com.example.focuspro.dtos.GameResultResponse;
import com.example.focuspro.dtos.GameResultSubmitRequest;
import com.example.focuspro.entities.Users;
import com.example.focuspro.services.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/game")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com"
}, allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS
})
public class GameController {

    @Autowired
    private GameService gameService;

    @PostMapping("/result")
    public GameResultResponse submitResult(@RequestBody GameResultSubmitRequest request) {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return gameService.submitResult(request, user);
    }

    /**
     * Returns the max unlocked level for every roadmap game this user has played.
     * Games the user has never played are absent from the list (client defaults to 1).
     */
    @GetMapping("/progress")
    public List<GameLevelProgressDTO> getLevelProgress() {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return gameService.getLevelProgress(user.getId());
    }
}
