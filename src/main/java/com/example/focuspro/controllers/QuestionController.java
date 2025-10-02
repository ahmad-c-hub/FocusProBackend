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
    public List<QuestionDTO> getBaselineQuestions(@PathVariable String level){
        return questionService.getTenQuestions(level);
    }

}
