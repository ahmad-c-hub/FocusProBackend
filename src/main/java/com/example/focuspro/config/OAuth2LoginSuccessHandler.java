package com.example.focuspro.config;

import com.example.focuspro.entities.Role;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.RoleRepo;
import com.example.focuspro.repos.UserRepo;
import com.example.focuspro.services.JWTService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Date;
import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private  UserRepo usersRepository;

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private  JWTService jwtService;


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        Role userRole = roleRepo.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        Optional<Users> optionalUser = usersRepository.findByUsername(email);
        Users user;
        if(optionalUser.isPresent()){
            user = optionalUser.get();
        }else{
            user = new Users();
            user.setUsername(email);
            user.setPassword("<PASSWORD>");
            user.setRole(userRole);
            user.setEmail(email);
            user.setName("Google User");
            user.setDob(Date.valueOf("1990-01-01"));
            usersRepository.save(user);
        }
        String jwtToken = jwtService.generateToken(user.getUsername());
        response.sendRedirect("http://localhost:/oauth2/redirect?token=" + jwtToken);

    }
}
