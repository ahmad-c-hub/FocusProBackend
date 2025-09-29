package com.example.focuspro.controllers;

import com.example.focuspro.dtos.BookDTO;
import com.example.focuspro.entities.Book;
import com.example.focuspro.services.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping( "/book")
@RestController
public class BookController {

    @Autowired
    private BookService bookService;

    @GetMapping("/recommended/{difficulty}")
    public List<BookDTO> getBooksByDifficulty(@PathVariable String difficulty){
        return bookService.getBooksByDifficulty(difficulty);
    }

    @GetMapping("/{title}")
    public BookDTO getBookByTitle(@PathVariable String title){
        return bookService.getBookByTitle(title);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/add-book")
    public BookDTO addBook(@RequestBody Book book){
        return bookService.addBook(book);
    }

}
