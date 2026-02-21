package com.example.test.controller;

import com.example.test.dto.CreateUserResponse;
import com.example.test.dto.FundWalletRequest;
import com.example.test.dto.TransferResponse;
import com.example.test.dto.WebTransferRequest;
import com.example.test.exception.BadRequestException;
import com.example.test.exception.ConflictException;
import com.example.test.exception.InsufficientFundsException;
import com.example.test.exception.ResourceNotFoundException;
import com.example.test.model.User;
import com.example.test.repo.UserRepo;
import com.example.test.service.ServiceCall;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WalletViewController {

    private final ServiceCall serviceCall;
    private final UserRepo userRepo;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        User user = userRepo.findByEmailIgnoreCase(email).orElseThrow();

        model.addAttribute("userId", user.getId());
        model.addAttribute("userEmail", user.getEmail());

        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest", new WebTransferRequest());
        }
        if (!model.containsAttribute("fundRequest")) {
            model.addAttribute("fundRequest", new FundWalletRequest());
        }

        try {
            CreateUserResponse wallet = serviceCall.getWalletForUser(email);
            model.addAttribute("hasWallet", true);
            model.addAttribute("wallet", wallet);
            model.addAttribute("entries", serviceCall.listEntriesForUser(email));
        } catch (RuntimeException ex) {
            model.addAttribute("hasWallet", false);
            model.addAttribute("entries", Collections.emptyList());
        }
        return "dashboard";
    }

    @PostMapping("/wallet/create")
    public String createWallet(Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            CreateUserResponse response = serviceCall.createWalletForUser(authentication.getName());
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Wallet created. Account number: " + response.getAccountNumber()
            );
        } catch (BadRequestException | ConflictException | ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected wallet creation error", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to create wallet at the moment.");
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/wallet/transfer")
    public String transfer(
            Authentication authentication,
            @Valid @ModelAttribute WebTransferRequest transferRequest,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage()
            );
            redirectAttributes.addFlashAttribute("transferRequest", transferRequest);
            return "redirect:/dashboard";
        }
        try {
            TransferResponse response = serviceCall.doTransferFromUserWallet(authentication.getName(), transferRequest);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Transfer successful: " + response.getAmount() + " sent to " + response.getToAccount()
            );
        } catch (BadRequestException | ConflictException | InsufficientFundsException | ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("transferRequest", transferRequest);
        } catch (RuntimeException ex) {
            log.error("Unexpected transfer error", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Transfer failed. Please try again.");
            redirectAttributes.addFlashAttribute("transferRequest", transferRequest);
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/wallet/fund")
    public String fundWallet(
            Authentication authentication,
            @Valid @ModelAttribute FundWalletRequest fundRequest,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage()
            );
            redirectAttributes.addFlashAttribute("fundRequest", fundRequest);
            return "redirect:/dashboard";
        }
        try {
            TransferResponse response = serviceCall.fundWalletForUser(authentication.getName(), fundRequest);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Wallet funded successfully: " + response.getAmount()
            );
        } catch (BadRequestException | ConflictException | InsufficientFundsException | ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("fundRequest", fundRequest);
        } catch (RuntimeException ex) {
            log.error("Unexpected fund wallet error", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to fund wallet right now.");
            redirectAttributes.addFlashAttribute("fundRequest", fundRequest);
        }
        return "redirect:/dashboard";
    }
}
