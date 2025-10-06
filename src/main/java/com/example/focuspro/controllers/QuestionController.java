package com.example.focuspro.controllers;

import com.example.focuspro.dtos.QuestionDTO;
import com.example.focuspro.services.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/question")
@RestController
@CrossOrigin(
        origins = {
                "http://localhost:3000",   // React
                "http://10.0.2.2:8080",
                "http://localhost:5000/"     // Android emulator access
        },
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @GetMapping("/test/{level}")
    public List<QuestionDTO> getTestQuestions(@PathVariable String level){
        return questionService.getTenQuestions(level);
    }

    @GetMapping("/test-answer/{id}")
    public boolean checkAnswer(@PathVariable int id, @RequestParam String answer){
        return questionService.checkAnswer(id,answer);
    }

}
