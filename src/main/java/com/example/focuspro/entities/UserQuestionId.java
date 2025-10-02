package com.example.focuspro.entities;

import java.io.Serializable;
import java.util.Objects;

public class UserQuestionId implements Serializable {
    private Integer userId;
    private Integer questionId;

    public UserQuestionId() {}
    public UserQuestionId(Integer userId, Integer questionId) {
        this.userId = userId;
        this.questionId = questionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserQuestionId)) return false;
        UserQuestionId that = (UserQuestionId) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(questionId, that.questionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, questionId);
    }
}

