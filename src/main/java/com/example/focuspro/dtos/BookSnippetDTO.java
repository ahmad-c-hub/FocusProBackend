package com.example.focuspro.dtos;

import lombok.*;
import java.math.BigDecimal;

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
    private Integer wordCount;
    private BigDecimal focusPoints;
}