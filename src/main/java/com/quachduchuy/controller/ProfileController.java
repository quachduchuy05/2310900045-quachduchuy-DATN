package com.quachduchuy.controller;

import com.quachduchuy.config.GoogleLoginSettings;
import com.quachduchuy.config.GoogleLoginSuccessHandler;
import jakarta.servlet.http.HttpSession;
import com.quachduchuy.dto.PasswordForm;
import com.quachduchuy.dto.ProfileForm;
import com.quachduchuy.entity.User;
import com.quachduchuy.exception.BusinessException;
import com.quachduchuy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Hồ sơ cá nhân và đổi mật khẩu. */
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final GoogleLoginSettings googleLoginSettings;

    @GetMapping
    public String page(Principal principal, Model model) {
        User user = userService.getCurrentUser(principal);
        model.addAttribute("profileForm", toForm(user));
        model.addAttribute("passwordForm", new PasswordForm());
        model.addAttribute("account", user);
        return "profile";
    }

    @PostMapping
    public String update(@Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult result,
                         Principal principal, Model model, RedirectAttributes redirect) {
        User user = userService.getCurrentUser(principal);
        if (result.hasErrors()) {
            model.addAttribute("passwordForm", new PasswordForm());
            model.addAttribute("account", user);
            return "profile";
        }
        userService.updateProfile(user, form);
        redirect.addFlashAttribute("success", "Đã cập nhật hồ sơ");
        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("passwordForm") PasswordForm form, BindingResult result,
                                 Principal principal, Model model, RedirectAttributes redirect) {
        User user = userService.getCurrentUser(principal);
        if (!result.hasErrors()) {
            try {
                userService.changePassword(user, form);
                redirect.addFlashAttribute("success", "Đã đổi mật khẩu");
                return "redirect:/profile";
            } catch (BusinessException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("profileForm", toForm(user));
        model.addAttribute("account", user);
        return "profile";
    }

    /** Bắt đầu liên kết Google cho tài khoản đang đăng nhập. */
    @PostMapping("/google")
    public String linkGoogle(Principal principal, HttpSession session) {
        if (!googleLoginSettings.isConfigured()) {
            throw new BusinessException("Đăng nhập Google chưa được cấu hình");
        }
        session.setAttribute(GoogleLoginSuccessHandler.LINK_ATTRIBUTE,
                userService.getCurrentUser(principal).getId());
        return "redirect:/oauth2/authorization/google";
    }

    private ProfileForm toForm(User user) {
        ProfileForm form = new ProfileForm();
        form.setFullName(user.getFullName());
        form.setPhone(user.getPhone());
        return form;
    }
}
