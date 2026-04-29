package com.example.focuspro.controllers;

import com.example.focuspro.dtos.ChangePasswordRequest;
import com.example.focuspro.dtos.CompleteProfileRequest;
import com.example.focuspro.dtos.UpdateProfileRequest;
import com.example.focuspro.entities.Users;
import com.example.focuspro.services.OAuthCodeStore;
import com.example.focuspro.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = {
        "http://localhost:3000", // React
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com" // Android emulator access
}, allowedHeaders = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE,
        RequestMethod.OPTIONS })

public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private OAuthCodeStore oAuthCodeStore;

    @PostMapping("/register")
    public String register(@RequestBody Users user) {
        return userService.register(user);
    }

    @PostMapping("/login")
    public String login(@RequestBody Users user) {
        return userService.login(user);
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        Users userNavigating = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userService.logout(request, userNavigating);
    }

    @GetMapping("/profile")
    public Users getProfile() {
        return userService.getProfile();
    }

    @PutMapping("/update-profile")
    public ResponseEntity<Void> updateProfile(
            @RequestBody(required = false) UpdateProfileRequest request) {
        Users userNavigating = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.updateProfile(userNavigating, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/complete-profile")
    public void completeProfile(@RequestBody CompleteProfileRequest request) {
        Users userNavigating = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.completeProfile(userNavigating, request);
    }

    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request) {
        Users userNavigating = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            userService.changePassword(userNavigating, request);
            return ResponseEntity.ok("Password changed successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/account")
    public ResponseEntity<String> deleteAccount() {
        Users userNavigating = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.deleteAccount(userNavigating);
        return ResponseEntity.ok("Account deleted.");
    }

    /**
     * Exchanges a short-lived one-time OAuth code (received via redirect URL) for
     * the real JWT.
     * The code expires in 60 seconds and is deleted on first use.
     */
    @GetMapping("/oauth/token")
    public ResponseEntity<String> exchangeOAuthCode(@RequestParam String code) {
        String jwt = oAuthCodeStore.exchange(code);
        if (jwt == null) {
            return ResponseEntity.badRequest().body("Invalid or expired code");
        }
        return ResponseEntity.ok(jwt);
    }

}
