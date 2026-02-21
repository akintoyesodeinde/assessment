package com.example.test.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class AuthResponse {
    private Long userId;
    private String email;
    private String accessToken;
    private String tokenType;
    private Instant expiresAt;
}
