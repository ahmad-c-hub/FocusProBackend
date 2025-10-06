package com.example.focuspro.controllers;

import com.example.focuspro.dtos.QuestionDTO;
import com.example.focuspro.services.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/question")
@RestController
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
    @PostMapping("/submit-test/baseline")
    public String submitTestBaseline(@RequestParam int score){
        return questionService.submitBaselineTestResults(score);
    }

}
