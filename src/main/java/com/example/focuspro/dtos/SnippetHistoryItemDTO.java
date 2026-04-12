package com.example.focuspro.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class SnippetHistoryItemDTO {

    private Integer snippetId;
    private String  snippetTitle;
    private Integer bookId;
    private String  bookTitle;
    private int     questionCount;

    /** "PASSED", "FAILED", or null if the user generated questions but never submitted answers. */
    private String  attemptResult;

    /** How many questions the user answered correctly (null if never submitted). */
    private Integer correctCount;

    /** Total questions in the submitted attempt (null if never submitted). */
    private Integer totalCount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    public SnippetHistoryItemDTO() {}

    public SnippetHistoryItemDTO(Integer snippetId, String snippetTitle,
                                  Integer bookId, String bookTitle,
                                  int questionCount, String attemptResult,
                                  Integer correctCount, Integer totalCount,
                                  LocalDateTime createdAt) {
        this.snippetId     = snippetId;
        this.snippetTitle  = snippetTitle;
        this.bookId        = bookId;
        this.bookTitle     = bookTitle;
        this.questionCount = questionCount;
        this.attemptResult = attemptResult;
        this.correctCount  = correctCount;
        this.totalCount    = totalCount;
        this.createdAt     = createdAt;
    }

    public Integer getSnippetId()     { return snippetId; }
    public String  getSnippetTitle()  { return snippetTitle; }
    public Integer getBookId()        { return bookId; }
    public String  getBookTitle()     { return bookTitle; }
    public int     getQuestionCount() { return questionCount; }
    public String  getAttemptResult() { return attemptResult; }
    public Integer getCorrectCount()  { return correctCount; }
    public Integer getTotalCount()    { return totalCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
