package com.example.test.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class CreateUserResponse {
    private Long userId;
    private String email;
    private String accountNumber;
    private BigDecimal balance;
}
