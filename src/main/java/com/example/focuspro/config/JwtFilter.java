package com.example.focuspro.config;
import com.example.focuspro.services.JWTService;
import com.example.focuspro.services.MyUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JWTService jwtService;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        System.out.println("[JwtFilter] >>> " + method + " " + path);

        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            System.out.println("[JwtFilter] Auth header present, token length=" + token.length());

            if (jwtService.isTokenRevoked(token)) {
                System.out.println("[JwtFilter] Token REVOKED");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Token has been revoked. Please login again.\"}");
                return;
            }

            try {
                username = jwtService.extractUserName(token);
                System.out.println("[JwtFilter] Extracted username: '" + username + "'");
            } catch (Exception e) {
                System.out.println("[JwtFilter] extractUserName FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Invalid or expired token. Please login again.\"}");
                return;
            }
        } else {
            System.out.println("[JwtFilter] No Bearer token in Authorization header (header=" + authHeader + ")");
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            System.out.println("[JwtFilter] Loading UserDetails for username='" + username + "'");
            try {
                UserDetails userDetails = applicationContext.getBean(MyUserDetailsService.class).loadUserByUsername(username);
                System.out.println("[JwtFilter] UserDetails loaded, db_username='" + userDetails.getUsername() + "'");

                boolean usernameMatch = username.equals(userDetails.getUsername());
                boolean notExpired = !jwtService.isTokenExpiredPublic(token);
                System.out.println("[JwtFilter] usernameMatch=" + usernameMatch + " (jwt='" + username + "' db='" + userDetails.getUsername() + "'), notExpired=" + notExpired);

                if (jwtService.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    System.out.println("[JwtFilter] Authentication SET successfully for '" + username + "'");
                } else {
                    System.out.println("[JwtFilter] validateToken returned FALSE — auth NOT set");
                }
            } catch (Exception e) {
                System.out.println("[JwtFilter] EXCEPTION in auth block: " + e.getClass().getName() + ": " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Authentication error: " + e.getMessage() + "\"}");
                return;
            }
        } else {
            if (username == null) {
                System.out.println("[JwtFilter] username is null — skipping auth setup");
            } else {
                System.out.println("[JwtFilter] Auth already set in context: " + SecurityContextHolder.getContext().getAuthentication());
            }
        }

        filterChain.doFilter(request, response);
    }
}
