package com.quachduchuy.controller;

import com.quachduchuy.dto.AiSuggestion;
import com.quachduchuy.dto.TripPlanRequest;
import com.quachduchuy.entity.User;
import com.quachduchuy.service.AiAssistantService;
import com.quachduchuy.service.GeminiService;
import com.quachduchuy.service.HomestayService;
import com.quachduchuy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

/** Trang Trợ lý AI: nhập điểm đến, ngân sách, sở thích → nhận lịch trình + phòng gợi ý. */
@Controller
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAssistantService aiAssistantService;
    private final GeminiService geminiService;
    private final HomestayService homestayService;
    private final UserService userService;

    @GetMapping
    public String page(Principal principal, Model model) {
        model.addAttribute("form", new TripPlanRequest());
        addCommon(principal, model);
        return "ai/assistant";
    }

    @PostMapping("/plan")
    public String plan(@Valid @ModelAttribute("form") TripPlanRequest form, BindingResult result,
                       Principal principal, Model model) {
        if (!result.hasErrors()) {
            AiSuggestion suggestion = aiAssistantService.planTrip(userService.getCurrentUser(principal), form);
            model.addAttribute("suggestion", suggestion);
        }
        addCommon(principal, model);
        return "ai/assistant";
    }

    private void addCommon(Principal principal, Model model) {
        User user = userService.getCurrentUser(principal);
        model.addAttribute("history", aiAssistantService.history(user));
        model.addAttribute("cities", homestayService.findAllCities());
        model.addAttribute("aiReady", geminiService.isConfigured());
    }
}
