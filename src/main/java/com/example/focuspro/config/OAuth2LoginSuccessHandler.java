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
    System.out.println("=== OAuth2LoginSuccessHandler START ===");
    
    OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
    String email = oAuth2User.getAttribute("email");
    System.out.println("Email: " + email);

    Role userRole = roleRepo.findByName("ROLE_USER")
            .orElseThrow(() -> new RuntimeException("Default role not found"));
    System.out.println("Role found: " + userRole.getName());

    Optional<Users> optionalUser = usersRepository.findByUsername(email);
    Users user;
    if(optionalUser.isPresent()){
        user = optionalUser.get();
        System.out.println("Existing user found");
    }else{
        user = new Users();
        user.setUsername(email);
        user.setPassword("OAUTH_USER");
        user.setRole(userRole);
        user.setEmail(email);
        user.setName("Google User");
        user.setDob(Date.valueOf("2000-01-01"));
        usersRepository.save(user);
        System.out.println("New user created");
    }
    
    String jwtToken = jwtService.generateToken(user.getUsername());
    System.out.println("JWT Token generated: " + jwtToken.substring(0, 20) + "...");
    
    String redirectUrl = "http://localhost:5000/#/oauth-callback?token=" + jwtToken;
    System.out.println("Redirecting to: " + redirectUrl);
    
    response.sendRedirect(redirectUrl);
    System.out.println("=== OAuth2LoginSuccessHandler END ===");
}
}
