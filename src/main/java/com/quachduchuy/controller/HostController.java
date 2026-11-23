package com.quachduchuy.controller;

import com.quachduchuy.dto.HomestayForm;
import com.quachduchuy.dto.RoomForm;
import com.quachduchuy.entity.BookingStatus;
import com.quachduchuy.entity.Homestay;
import com.quachduchuy.entity.Room;
import com.quachduchuy.entity.User;
import com.quachduchuy.service.BookingService;
import com.quachduchuy.service.HomestayService;
import com.quachduchuy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Khu vực CHỦ NHÀ (/host/**): quản lý homestay, phòng và đơn đặt của mình. */
@Controller
@RequestMapping("/host")
@RequiredArgsConstructor
public class HostController {

    private final HomestayService homestayService;
    private final BookingService bookingService;
    private final UserService userService;

    @GetMapping
    public String dashboard(Principal principal, Model model) {
        User host = userService.getCurrentUser(principal);
        model.addAttribute("homestays", homestayService.findByHost(host));
        model.addAttribute("bookings", bookingService.findByHost(host));
        return "host/dashboard";
    }

    // ===================== HOMESTAY =====================

    @GetMapping("/homestays/new")
    public String newHomestay(Model model) {
        model.addAttribute("form", new HomestayForm());
        model.addAttribute("homestayId", null);
        return "host/homestay-form";
    }

    @PostMapping("/homestays/new")
    public String createHomestay(@Valid @ModelAttribute("form") HomestayForm form, BindingResult result,
                                 Principal principal, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "host/homestay-form";
        }
        Homestay h = homestayService.createHomestay(userService.getCurrentUser(principal), form);
        redirect.addFlashAttribute("success", "Đã tạo homestay. Hãy thêm phòng cho homestay này.");
        return "redirect:/host/homestays/" + h.getId() + "/rooms";
    }

    @GetMapping("/homestays/{id}/edit")
    public String editHomestay(@PathVariable Long id, Principal principal, Model model) {
        Homestay h = homestayService.getOwnedHomestay(userService.getCurrentUser(principal), id);
        model.addAttribute("form", homestayService.toForm(h));
        model.addAttribute("homestayId", id);
        return "host/homestay-form";
    }

    @PostMapping("/homestays/{id}/edit")
    public String updateHomestay(@PathVariable Long id, @Valid @ModelAttribute("form") HomestayForm form,
                                 BindingResult result, Principal principal, Model model,
                                 RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("homestayId", id);
            return "host/homestay-form";
        }
        homestayService.updateHomestay(userService.getCurrentUser(principal), id, form);
        redirect.addFlashAttribute("success", "Đã cập nhật homestay");
        return "redirect:/host";
    }

    @PostMapping("/homestays/{id}/delete")
    public String deleteHomestay(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        try {
            homestayService.deleteHomestay(userService.getCurrentUser(principal), id);
            redirect.addFlashAttribute("success", "Đã xóa homestay");
        } catch (Exception e) {
            // Không xóa được khi đã có đơn đặt (ràng buộc khóa ngoại) → gợi ý ẩn thay vì xóa
            redirect.addFlashAttribute("error", "Không thể xóa homestay đã có đơn đặt phòng. Hãy tạm ngưng các phòng thay vì xóa.");
        }
        return "redirect:/host";
    }

    // ===================== PHÒNG =====================

    @GetMapping("/homestays/{id}/rooms")
    public String rooms(@PathVariable Long id, Principal principal, Model model) {
        Homestay h = homestayService.getOwnedHomestay(userService.getCurrentUser(principal), id);
        model.addAttribute("homestay", h);
        model.addAttribute("rooms", homestayService.getRooms(h));
        return "host/rooms";
    }

    @GetMapping("/homestays/{homestayId}/rooms/new")
    public String newRoom(@PathVariable Long homestayId, Model model) {
        model.addAttribute("form", new RoomForm());
        model.addAttribute("homestayId", homestayId);
        model.addAttribute("roomId", null);
        return "host/room-form";
    }

    @PostMapping("/homestays/{homestayId}/rooms/new")
    public String createRoom(@PathVariable Long homestayId, @Valid @ModelAttribute("form") RoomForm form,
                             BindingResult result, Principal principal, Model model,
                             RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("homestayId", homestayId);
            model.addAttribute("roomId", null);
            return "host/room-form";
        }
        homestayService.addRoom(userService.getCurrentUser(principal), homestayId, form);
        redirect.addFlashAttribute("success", "Đã thêm phòng");
        return "redirect:/host/homestays/" + homestayId + "/rooms";
    }

    @GetMapping("/rooms/{roomId}/edit")
    public String editRoom(@PathVariable Long roomId, Principal principal, Model model) {
        Room r = homestayService.getOwnedRoom(userService.getCurrentUser(principal), roomId);
        model.addAttribute("form", homestayService.toForm(r));
        model.addAttribute("homestayId", r.getHomestay().getId());
        model.addAttribute("roomId", roomId);
        return "host/room-form";
    }

    @PostMapping("/rooms/{roomId}/edit")
    public String updateRoom(@PathVariable Long roomId, @Valid @ModelAttribute("form") RoomForm form,
                             BindingResult result, Principal principal, Model model,
                             RedirectAttributes redirect) {
        User user = userService.getCurrentUser(principal);
        Long homestayId = homestayService.getOwnedRoom(user, roomId).getHomestay().getId();
        if (result.hasErrors()) {
            model.addAttribute("homestayId", homestayId);
            model.addAttribute("roomId", roomId);
            return "host/room-form";
        }
        homestayService.updateRoom(user, roomId, form);
        redirect.addFlashAttribute("success", "Đã cập nhật phòng");
        return "redirect:/host/homestays/" + homestayId + "/rooms";
    }

    @PostMapping("/rooms/{roomId}/delete")
    public String deleteRoom(@PathVariable Long roomId, Principal principal, RedirectAttributes redirect) {
        User user = userService.getCurrentUser(principal);
        Long homestayId = homestayService.getOwnedRoom(user, roomId).getHomestay().getId();
        try {
            homestayService.deleteRoom(user, roomId);
            redirect.addFlashAttribute("success", "Đã xóa phòng");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Phòng đã có đơn đặt nên không thể xóa. Hãy chuyển sang 'Tạm ngưng'.");
        }
        return "redirect:/host/homestays/" + homestayId + "/rooms";
    }

    // ===================== ĐƠN ĐẶT =====================

    @PostMapping("/bookings/{id}/status")
    public String updateBookingStatus(@PathVariable Long id, @RequestParam BookingStatus status,
                                      Principal principal, RedirectAttributes redirect) {
        bookingService.updateStatusByManager(userService.getCurrentUser(principal), id, status);
        redirect.addFlashAttribute("success", "Đã cập nhật đơn #" + id);
        return "redirect:/host";
    }
}
