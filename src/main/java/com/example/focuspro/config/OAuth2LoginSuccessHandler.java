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
        if(optionalUser.isPresent()){
            Users user = optionalUser.get();
            String jwtToken = jwtService.generateToken(user.getUsername());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"token\":\"" + jwtToken + "\"}");
            response.getWriter().flush();
        }else{
            Users users = new Users();
            users.setUsername(email);
            users.setPassword("<PASSWORD>");
            users.setRole(userRole);
            users.setEmail(email);
            users.setName("Google User");
            users.setDob(Date.valueOf("1990-01-01"));
            usersRepository.save(users);
        }

    }
}
