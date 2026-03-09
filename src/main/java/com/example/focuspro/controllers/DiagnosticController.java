package com.example.focuspro.controllers;

import com.example.focuspro.dtos.*;
import com.example.focuspro.services.DiagnosticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/diagnostic")
@CrossOrigin(
        origins = {
                "http://localhost:3000",
                "http://10.0.2.2:8080",
                "http://localhost:5000/"
        },
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
public class DiagnosticController {

    @Autowired
    private DiagnosticService diagnosticService;

    // GET /diagnostic/questions
    @GetMapping("/questions")
    public List<DiagnosticQuestionDTO> getQuestions() {
        return diagnosticService.getAllQuestions();
    }

    // POST /diagnostic/submit
    @PostMapping("/submit")
    public String submitDiagnostic(@RequestBody DiagnosticSubmitRequest request) {
        return diagnosticService.submitDiagnostic(request);
    }
}