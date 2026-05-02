package com.example.focuspro.services;

import com.example.focuspro.dtos.ChangePasswordRequest;
import com.example.focuspro.dtos.CompleteProfileRequest;
import com.example.focuspro.dtos.UpdateProfileRequest;
import com.example.focuspro.entities.Role;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.*;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired private ActivityLogRepo activityLogRepo;
    @Autowired private AiGeneratedQuestionRepo aiGeneratedQuestionRepo;
    @Autowired private AppScreenEventRepo appScreenEventRepo;
    @Autowired private CoachingSessionRepo coachingSessionRepo;
    @Autowired private DailyAppUsageRepo dailyAppUsageRepo;
    @Autowired private DailyChallengeRepo dailyChallengeRepo;
    @Autowired private DailyGameScoreRepo dailyGameScoreRepo;
    @Autowired private DailyGoalRepo dailyGoalRepo;
    @Autowired private DailyScoreRepo dailyScoreRepo;
    @Autowired private FocusScheduleRepo focusScheduleRepo;
    @Autowired private GameLevelProgressRepo gameLevelProgressRepo;
    @Autowired private GameResultRepo gameResultRepo;
    @Autowired private GoalNotificationRepo goalNotificationRepo;
    @Autowired private HabitLogRepo habitLogRepo;
    @Autowired private HabitRepo habitRepo;
    @Autowired private LockInSessionRepo lockInSessionRepo;
    @Autowired private RoomMessageRepository roomMessageRepository;
    @Autowired private WebPushSubscriptionRepo webPushSubscriptionRepo;

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
        user.setLastLogin(java.time.OffsetDateTime.now());
        Role role = roleRepo.findByName("ROLE_USER").orElseThrow(() -> new IllegalArgumentException("Role not found"));
        user.setRole(role);
        userRepository.save(user);
        activityLogService.log(user.getId(), "REGISTER", "New account created");

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

        Optional<Users> existingUser = userRepository.findByUsername(username);

        if (existingUser.isEmpty()) {
            throw new IllegalArgumentException("No account found with that username.");
        }

        Users found = existingUser.get();

        if (found.isGoogleUser()) {
            throw new IllegalArgumentException("This account uses Google Sign-In. Please log in with Google.");
        }

        try {
            Authentication authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password));
            if (authentication.isAuthenticated()) {
                found.setLastLogin(java.time.OffsetDateTime.now());
                userRepository.save(found);
                activityLogService.log(found.getId(), "LOGIN", "User logged in");
                return jwtService.generateToken(username);
            } else {
                throw new IllegalArgumentException("Incorrect password.");
            }
        } catch (AuthenticationException e) {
            // Wrap with a plain string so getMessage() returns the human-readable message,
            // not the full BadCredentialsException toString().
            throw new IllegalArgumentException("Incorrect password.");
        }
    }

    public String logout(HttpServletRequest request, Users userNavigating) {
        String authHeader = request.getHeader("Authorization");
        System.out.println("Auth Header: " + authHeader);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            activityLogService.log(userNavigating.getId(), "LOGOUT", "User logged out");
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

    /// Called when a Google user fills in their missing profile info.
    public void completeProfile(Users userNavigating, CompleteProfileRequest request) {
        Optional<Users> optionalUser = userRepository.findByUsername(userNavigating.getUsername());
        if (optionalUser.isEmpty()) throw new IllegalArgumentException("User not found.");
        Users user = optionalUser.get();
        if (request.getDob() != null && !request.getDob().isBlank()) {
            user.setDob(Date.valueOf(request.getDob()));
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        userRepository.save(user);
        activityLogService.log(user.getId(), "PROFILE_COMPLETE", "User completed their profile");
    }

    public void activateConsent(Users userNavigating) {
        Optional<Users> optionalUser = userRepository.findByUsername(userNavigating.getUsername());
        if(optionalUser.isPresent()){
            Users user = optionalUser.get();
            user.setConsentUsage(true);
            user.setConsentAt(java.time.OffsetDateTime.now());
            userRepository.save(user);
        }else{
            throw new IllegalArgumentException("User not found.");
        }
    }

    public void updateProfile(Users userNavigating, UpdateProfileRequest request) {
        Optional<Users> optionalUser = userRepository.findByUsername(userNavigating.getUsername());
        if (optionalUser.isEmpty()) throw new IllegalArgumentException("User not found.");
        Users user = optionalUser.get();
        // Activate consent (keep existing behaviour)
        user.setConsentUsage(true);
        user.setConsentAt(java.time.OffsetDateTime.now());
        // Update name if provided
        if (request != null && request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        user.setUpdatedAt(java.time.OffsetDateTime.now());
        userRepository.save(user);
        activityLogService.log(user.getId(), "PROFILE_UPDATE", "User updated their profile");
    }

    public void changePassword(Users userNavigating, ChangePasswordRequest request) {
        if (request.getCurrentPassword() == null || request.getNewPassword() == null) {
            throw new IllegalArgumentException("Both current and new password are required.");
        }
        if (request.getNewPassword().length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
        Optional<Users> optionalUser = userRepository.findByUsername(userNavigating.getUsername());
        if (optionalUser.isEmpty()) throw new IllegalArgumentException("User not found.");
        Users user = optionalUser.get();
        if (!bCryptPasswordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        user.setPassword(bCryptPasswordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(java.time.OffsetDateTime.now());
        userRepository.save(user);
        activityLogService.log(user.getId(), "PASSWORD_CHANGE", "User changed their password");
    }

    @Transactional
    public void deleteAccount(Users userNavigating) {
        Optional<Users> optionalUser = userRepository.findByUsername(userNavigating.getUsername());
        if (optionalUser.isEmpty()) throw new IllegalArgumentException("User not found.");
        int userId = optionalUser.get().getId();

        // Delete child records in dependency order before removing the user row.
        // HabitLog before Habit (habit_id FK); GoalNotification before DailyGoal (goal_id FK).
        habitLogRepo.deleteByUserId(userId);
        goalNotificationRepo.deleteByUserId(userId);
        webPushSubscriptionRepo.deleteByUserId(userId);
        activityLogRepo.deleteByUserId(userId);
        aiGeneratedQuestionRepo.deleteByUserId(userId);
        appScreenEventRepo.deleteByUserId(userId);
        coachingSessionRepo.deleteByUserId(userId);
        dailyAppUsageRepo.deleteByUserId(userId);
        dailyChallengeRepo.deleteByUserId(userId);
        dailyGameScoreRepo.deleteByUserId(userId);
        dailyGoalRepo.deleteByUserId(userId);
        dailyScoreRepo.deleteByUserId(userId);
        focusScheduleRepo.deleteByUserId(userId);
        gameLevelProgressRepo.deleteByUserId(userId);
        gameResultRepo.deleteByUserId(userId);
        habitRepo.deleteByUserId(userId);
        lockInSessionRepo.deleteByUserId(userId);
        roomMessageRepository.deleteByUserId(userId);

        userRepository.deleteById(userId);
    }

    public void validateSignup(String username, String email) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username cannot be empty");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email cannot be empty");
        if (userRepository.findByUsername(username.trim()).isPresent())
            throw new IllegalArgumentException("Username not available");
        if (userRepository.findByEmail(email.trim().toLowerCase()).isPresent())
            throw new IllegalArgumentException("Email already registered");
    }

    public boolean emailExists(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase()).isPresent();
    }

    public void validateForgotPassword(String username, String email) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username cannot be empty.");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email cannot be empty.");

        Optional<Users> optionalUser = userRepository.findByUsername(username.trim());
        if (optionalUser.isEmpty())
            throw new IllegalArgumentException("No account found with that username.");

        Users user = optionalUser.get();
        if (!user.getEmail().equalsIgnoreCase(email.trim()))
            throw new IllegalArgumentException("The email address does not match this account.");
        if (user.isGoogleUser())
            throw new IllegalArgumentException("This account uses Google Sign-In and cannot use password reset.");
    }

    public void resetPassword(String email, String newPassword) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required.");
        if (newPassword == null || newPassword.length() < 8)
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        Optional<Users> optionalUser = userRepository.findByEmail(email.trim().toLowerCase());
        if (optionalUser.isEmpty()) throw new IllegalArgumentException("No account found with that email.");
        Users user = optionalUser.get();
        if (user.isGoogleUser())
            throw new IllegalArgumentException("This account uses Google Sign-In and cannot use password reset.");
        user.setPassword(bCryptPasswordEncoder.encode(newPassword));
        user.setUpdatedAt(java.time.OffsetDateTime.now());
        userRepository.save(user);
        activityLogService.log(user.getId(), "PASSWORD_RESET", "User reset their password via OTP");
    }
}
