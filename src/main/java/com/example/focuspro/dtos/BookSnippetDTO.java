package com.example.focuspro.dtos;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BookSnippetDTO {
    private Integer id;
    private Integer bookId;
    private String snippetTitle;
    private String snippetText;
    private String snippetAudioUrl;
    private Integer pageNumber;
    private Integer durationSeconds;
    private Integer sequenceOrder;
    private boolean isCompleted;
}