package com.example.focuspro.config;

import com.example.focuspro.entities.Role;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.RoleRepo;
import com.example.focuspro.repos.UserRepo;
import com.example.focuspro.services.JWTService;
import com.example.focuspro.services.OAuthCodeStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private UserRepo usersRepository;

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private OAuthCodeStore oAuthCodeStore;

    // BCryptPasswordEncoder is stateless — no Spring dependency — so we create it
    // directly here instead of injecting it. Injecting it would cause a circular
    // dependency: OAuth2LoginSuccessHandler → BCryptPasswordEncoder bean →
    // SecurityConfig
    // → OAuth2LoginSuccessHandler.
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

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

        Optional<Users> optionalUser = usersRepository.findByEmail(email);
        Users user;
        if (optionalUser.isPresent()) {
            user = optionalUser.get();
            user.setLastLogin(OffsetDateTime.now());
            System.out.println("Existing user found");
        } else {
            user = new Users();
            user.setUsername(email.substring(0, email.indexOf("@")));
            // Store a hashed random token — never a readable plain-text string.
            // This means the password column is always a proper BCrypt hash,
            // and no one can guess or reuse this value to log in via the password endpoint.
            user.setPassword(bCryptPasswordEncoder.encode("GOOGLE_OAUTH_" + UUID.randomUUID()));
            user.setRole(userRole);
            user.setEmail(email);
            user.setName(email.substring(0, email.indexOf("@")));
            // DOB intentionally left null — the app will prompt the user to complete their
            // profile.
            user.setCreatedAt(OffsetDateTime.now());
            user.setGoogleUser(true);
            user.setLastLogin(OffsetDateTime.now());
            usersRepository.save(user);
            System.out.println("New user created");
        }

        String jwtToken = jwtService.generateToken(user.getUsername());
        System.out.println("JWT Token generated: " + jwtToken.substring(0, 20) + "...");

        // Store the token server-side and redirect with a short-lived one-time code.
        // This prevents the real JWT from appearing in browser history or server logs.
        String code = oAuthCodeStore.store(jwtToken);
        String redirectUrl = "http://https://focuspro-fm2d.onrender.com/#/oauth-callback?code=" + code;
        System.out.println("Redirecting to: " + redirectUrl);

        response.sendRedirect(redirectUrl);
        System.out.println("=== OAuth2LoginSuccessHandler END ===");
    }
}
