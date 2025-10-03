package com.example.focuspro.services;

import com.example.focuspro.dtos.QuestionDTO;
import com.example.focuspro.entities.Question;
import com.example.focuspro.entities.UserQuestion;
import com.example.focuspro.entities.UserQuestionId;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.QuestionRepo;
import com.example.focuspro.repos.UserQuestionRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class QuestionService {

    @Autowired
    private QuestionRepo questionRepo;

    @Autowired
    private UserQuestionRepo userQuestionRepo;


    public List<QuestionDTO> getTenQuestions(String level) {
        Users usersNavigating = (Users) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<Question> questions = questionRepo.getTenRandomUnansweredQuestions(level,usersNavigating.getId());
        List<QuestionDTO> dtos = new java.util.ArrayList<>();
        for (Question question : questions) {
            QuestionDTO dto = new QuestionDTO(question.getId(),question.getQuestionText(),question.getOptionA(),question.getOptionB(),
                    question.getOptionC(),question.getOptionD(),question.getCorrectAnswer(), question.getPictureUrl(),
                    question.getType(),question.getLevel(),getPower(question.getLevel(),question.getType()));
            dtos.add(dto);
        }
        return dtos;
    }

    public double getPower(String level, String type) {
        double levelWeight = switch (level.toLowerCase()) {
            case "baseline" -> 1.0;
            case "easy"     -> 1.5;
            case "medium"   -> 2.0;
            case "hard"     -> 3.0;
            default         -> 1.0;
        };

        double typeWeight = switch (type.toLowerCase()) {
            case "stroop"      -> 1.2;
            case "nback"       -> 1.5;
            case "reaction"    -> 1.3;
            case "iq_pattern"  -> 1.8;
            case "iq_verbal"   -> 1.6;
            case "iq_matrix"   -> 2.0;
            default            -> 1.0;
        };

        return levelWeight * typeWeight;
    }


    public boolean checkAnswer(int id, String answer) {
        Users userNavigating = (Users) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<Question> questionOptional = questionRepo.findById(id);
        if(questionOptional.isEmpty()){
            throw new IllegalArgumentException("Question not correct.");
        }
        Question question = questionOptional.get();
        if(question.getCorrectAnswer().equals(answer)){
            UserQuestion userQuestion = new UserQuestion(userNavigating.getId(), id);
            userQuestionRepo.save(userQuestion);
            return true;
        }
        return false;
    }
}
