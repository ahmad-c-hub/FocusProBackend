package com.example.focuspro.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class BookDTO {
    private String title;
    private String author;
    private String description;
    private String audioUrl;
    private String coverUrl;
    private String bookLength;
    private String audioLength;
}
