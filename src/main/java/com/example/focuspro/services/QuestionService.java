package com.example.focuspro.services;

import com.example.focuspro.dtos.QuestionDTO;
import com.example.focuspro.entities.Question;
import com.example.focuspro.repos.QuestionRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionService {

    @Autowired
    private QuestionRepo questionRepo;


    public List<QuestionDTO> getTenQuestions(String level) {
        List<Question> questions = questionRepo.getTenRandomQuestions(level);
        List<QuestionDTO> dtos = new java.util.ArrayList<>();
        for (Question question : questions) {
            QuestionDTO dto = new QuestionDTO(question.getQuestionText(),question.getOptionA(),question.getOptionB(),
                    question.getOptionC(),question.getOptionD(),question.getCorrectAnswer(), question.getPictureUrl(),
                    question.getType(),question.getLevel(),0.0);
            dtos.add(dto);
        }
        return dtos;
    }
}
