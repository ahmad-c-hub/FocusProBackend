package com.example.focuspro.controllers;

import com.example.focuspro.dtos.ChangePasswordRequest;
import com.example.focuspro.dtos.CompleteProfileRequest;
import com.example.focuspro.dtos.ResetPasswordRequest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com"
}, allowedHeaders = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE,
        RequestMethod.OPTIONS })

public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

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

    // ── Sign-up pre-validation ────────────────────────────────────────────────

    /**
     * Called before OTP is sent so the user sees username/email errors immediately
     * rather than after entering their verification code.
     */
    @PostMapping("/validate-signup")
    public ResponseEntity<String> validateSignup(@RequestBody Map<String, String> body) {
        try {
            userService.validateSignup(body.get("username"), body.get("email"));
            return ResponseEntity.ok("Valid");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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
            log.error("[send-otp] Resend API error for {}: {}", email, e.getMessage(), e);
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
        try {
            userService.deleteAccount(userNavigating);
            return ResponseEntity.ok("Account deleted.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    /**
     * Step 1: User submits their email. If registered, generate an OTP and email it.
     * Always returns 200 to avoid leaking whether an email is registered.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required.");
        }
        if (!userService.emailExists(email)) {
            return ResponseEntity.ok("If this email is registered, a reset code has been sent.");
        }
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        otpStore.store(email, otp);
        try {
            emailService.sendOtp(email, otp);
            return ResponseEntity.ok("If this email is registered, a reset code has been sent.");
        } catch (Exception e) {
            log.error("[forgot-password] Email error for {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send reset email: " + e.getMessage());
        }
    }

    /**
     * Step 3: After OTP is verified, set the new password.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        if (!otpStore.isVerified(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Email not verified. Please verify your OTP first.");
        }
        try {
            userService.resetPassword(request.getEmail(), request.getNewPassword());
            otpStore.clearVerified(request.getEmail());
            return ResponseEntity.ok("Password reset successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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
