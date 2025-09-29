package com.example.focuspro.services;

import com.example.focuspro.dtos.BookDTO;
import com.example.focuspro.entities.Book;
import com.example.focuspro.repos.BookRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BookService {

    @Autowired
    private BookRepo bookRepo;

    public BookDTO toBookDTO(Book book){
        BookDTO dto = new BookDTO();
        dto.setBookLength(book.getBook_length());
        dto.setAuthor(book.getAuthor());
        dto.setTitle(book.getTitle());
        dto.setDescription(book.getDescription());
        dto.setAudioLength(book.getAudio_length());
        dto.setAudioUrl("public/audios/"+book.getTitle()+".mp3");
        dto.setCoverUrl("public/images/"+book.getTitle()+".jpg");
        return dto;
    }




    public List<BookDTO> getBooksByDifficulty(String difficulty) {
        List<Book> books = bookRepo.getBooksByDifficulty(difficulty);
        List<BookDTO> dtos = new ArrayList<>();
        for (Book book : books) {
            BookDTO dto = toBookDTO(book);
            dtos.add(dto);
        }
        return dtos;
    }

    public BookDTO addBook(Book book) {
        if(book.getAuthor()==null || book.getDescription()==null ||  book.getTitle()==null
        || book.getBook_length()==null || book.getAudio_length()==null || book.getDifficulty_level()==null){
            throw new IllegalArgumentException("Invalid, please fill in all the fields.");
        }
        Optional<Book> optional = bookRepo.getBookByTitle(book.getTitle());
        if(optional.isPresent()){
            throw new IllegalArgumentException("Book already exists.");
        }
        bookRepo.save(book);
        return toBookDTO(book);
    }

    public BookDTO getBookByTitle(String title) {
        Optional<Book> optional = bookRepo.getBookByTitle(title);
        if(optional.isEmpty()){
            throw new IllegalArgumentException("Book does not exist.");
        }
        return toBookDTO(optional.get());
    }
}
