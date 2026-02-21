package com.example.test.controller;

import com.example.test.dto.AuthResponse;
import com.example.test.dto.LoginRequest;
import com.example.test.dto.RegisterRequest;
import com.example.test.exception.ResourceNotFoundException;
import com.example.test.model.User;
import com.example.test.repo.UserRepo;
import com.example.test.security.JwtTokenService;
import com.example.test.service.ServiceCall;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final ServiceCall serviceCall;
    private final UserRepo userRepo;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        serviceCall.registerUser(request);
        AuthResponse response = issueToken(normalizeEmail(request.getEmail()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );
        AuthResponse response = issueToken(authentication.getName());
        return ResponseEntity.ok(response);
    }

    private AuthResponse issueToken(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepo.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        JwtTokenService.TokenDetails tokenDetails = jwtTokenService.generateToken(normalizedEmail);
        return new AuthResponse(
                user.getId(),
                normalizedEmail,
                tokenDetails.token(),
                "Bearer",
                tokenDetails.expiresAt()
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
