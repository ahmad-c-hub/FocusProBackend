package com.example.focuspro.controllers;

import com.example.focuspro.dtos.ChangePasswordRequest;
import com.example.focuspro.dtos.CompleteProfileRequest;
import com.example.focuspro.dtos.UpdateProfileRequest;
import com.example.focuspro.entities.Users;
import com.example.focuspro.services.EmailService;
import com.example.focuspro.services.OAuthCodeStore;
import com.example.focuspro.services.OtpStore;
import com.example.focuspro.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Map;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com"
}, allowedHeaders = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE,
        RequestMethod.OPTIONS })

public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private OAuthCodeStore oAuthCodeStore;

    @Autowired
    private OtpStore otpStore;

    @Autowired
    private EmailService emailService;

    // ── Auth ──────────────────────────────────────────────────────────────────

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

    // ── OTP Email Verification ────────────────────────────────────────────────

    /**
     * Step 1: Generate a 6-digit OTP, store it in OtpStore, and email it to the user.
     * Called before registration so we can confirm the email address exists.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        otpStore.store(email, otp);
        try {
            emailService.sendOtp(email, otp);
            return ResponseEntity.ok("OTP sent");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send verification email: " + e.getMessage());
        }
    }

    /**
     * Step 2: Verify the OTP the user typed in.
     * On success, OtpStore marks the email as verified so /register can proceed.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp   = body.get("otp");
        if (email == null || otp == null) {
            return ResponseEntity.badRequest().body("Email and OTP are required");
        }
        if (otpStore.verify(email, otp)) {
            return ResponseEntity.ok("Email verified");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired OTP");
    }

    // ── Profile ───────────────────────────────────────────────────────────────

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

    // ── OAuth Code Exchange ───────────────────────────────────────────────────

    /**
     * Exchanges a short-lived one-time OAuth code (received via redirect URL) for
     * the real JWT. The code expires in 5 minutes and is deleted on first use.
     */
    @PostMapping("/oauth/token")
    public ResponseEntity<Map<String, String>> exchangeOAuthCode(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String jwt = oAuthCodeStore.exchange(code);
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        return ResponseEntity.ok(Map.of("token", jwt));
    }
}
