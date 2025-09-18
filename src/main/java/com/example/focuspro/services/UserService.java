package com.example.focuspro.services;

import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.UserRepo;

import javax.crypto.IllegalBlockSizeException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepo userRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;



    public void register(Users user) {
        if(userRepository.findByUsername(user.getUsername()).isPresent()){
            throw new IllegalArgumentException("Username not available");
        }
        if(user.getPassword().isEmpty()){
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if(user.getEmail().isEmpty()){
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if(user.getName().isEmpty()){
            throw new IllegalArgumentException("Name cannot be empty");
        }
        if(user.getDob() == null){
            throw new IllegalArgumentException("Date of birth cannot be empty");
        }
        if(user.getCreatedAt() == null){
            throw new IllegalArgumentException("Date of birth cannot be empty");
        }
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        userRepository.save(user);
    }
}
