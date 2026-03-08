package com.example.focuspro.services;

import com.example.focuspro.entities.Role;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.RoleRepo;
import com.example.focuspro.repos.UserRepo;

import javax.crypto.IllegalBlockSizeException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepo userRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private RoleRepo roleRepo;

    public String register(Users user) {
        if (user.getUsername() == null || user.getEmail() == null || user.getPassword() == null
                || user.getName() == null || user.getDob() == null) {
            throw new IllegalArgumentException("You must fill in all the fields");
        }

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username not available");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        user.setCreatedAt(java.time.OffsetDateTime.now());
        user.setLastLogin(java.time.OffsetDateTime.now()); // ADD THIS
        Role role = roleRepo.findByName("ROLE_USER").orElseThrow(() -> new IllegalArgumentException("Role not found"));
        user.setRole(role);
        userRepository.save(user);

        // Generate and return JWT token instead of success message
        return jwtService.generateToken(user.getUsername());
    }

    public String login(Users user1) {
        String username = user1.getUsername();
        String password = user1.getPassword();
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        try {
            Authentication authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password));
            if (authentication.isAuthenticated()) {
                Users user = userRepository.findByUsername(username).get();
                user.setLastLogin(java.time.OffsetDateTime.now());
                userRepository.save(user);
                return jwtService.generateToken(username);
            } else {
                throw new IllegalArgumentException("Incorrect username or password");
            }
        } catch (AuthenticationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String logout(HttpServletRequest request, Users userNavigating) {
        String authHeader = request.getHeader("Authorization");
        System.out.println("Auth Header: " + authHeader);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            jwtService.revokeToken(token);
            return "Logged out successfully! ";
        }
        return "No token found!";
    }

    public Users getProfile() {
        Users userNavigating = (Users) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<Users> optionalUser = userRepository.findByUsername(userNavigating.getUsername());
        if(optionalUser.isEmpty()){
            throw new IllegalArgumentException("User not found.");
        }
        return optionalUser.get();
    }
}
