package com.example.test.controller;

import com.example.test.dto.RegisterRequest;
import com.example.test.exception.BadRequestException;
import com.example.test.exception.ConflictException;
import com.example.test.exception.ResourceNotFoundException;
import com.example.test.service.ServiceCall;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthViewController {

    private final ServiceCall serviceCall;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!model.containsAttribute("registerRequest")) {
            model.addAttribute("registerRequest", new RegisterRequest());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute RegisterRequest registerRequest,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage()
            );
            redirectAttributes.addFlashAttribute("registerRequest", registerRequest);
            return "redirect:/register";
        }

        try {
            Long userId = serviceCall.registerUser(registerRequest);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Registration successful. Your user ID is " + userId + ". Please sign in."
            );
            return "redirect:/login?registered";
        } catch (BadRequestException | ConflictException | ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("registerRequest", registerRequest);
            return "redirect:/register";
        } catch (RuntimeException ex) {
            log.error("Unexpected error during registration", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Registration failed. Please try again.");
            redirectAttributes.addFlashAttribute("registerRequest", registerRequest);
            return "redirect:/register";
        }
    }
}
