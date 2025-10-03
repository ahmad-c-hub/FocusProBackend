package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_questions")
@IdClass(UserQuestionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserQuestion {

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Id
    @Column(name = "question_id", nullable = false)
    private Integer questionId;
}

