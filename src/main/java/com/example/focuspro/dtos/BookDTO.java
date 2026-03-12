package com.example.focuspro.dtos;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BookDTO {
    private Integer id;
    private String title;
    private String author;
    private Integer level;
    private String category;
    private String description;
    private String audioUrl;
    private String bookPagesUrl;
    private Integer totalPages;
}