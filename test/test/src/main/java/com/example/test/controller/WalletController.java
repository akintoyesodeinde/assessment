package com.example.test.controller;

import com.example.test.dto.CreateUserRequest;
import com.example.test.dto.CreateUserResponse;
import com.example.test.dto.DoTransDto;
import com.example.test.dto.FundWalletRequest;
import com.example.test.dto.TransferResponse;
import com.example.test.service.ServiceCall;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final ServiceCall serviceCall;

    @PostMapping("/users")
    public ResponseEntity<CreateUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceCall.createUserAndAccount(request));
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody DoTransDto request) {
        return ResponseEntity.ok(serviceCall.doIntraTransfer(request));
    }

    @PostMapping("/fund/me")
    public ResponseEntity<TransferResponse> fundMyWallet(
            Authentication authentication,
            @Valid @RequestBody FundWalletRequest request
    ) {
        return ResponseEntity.ok(serviceCall.fundWalletForUser(authentication.getName(), request));
    }
}
