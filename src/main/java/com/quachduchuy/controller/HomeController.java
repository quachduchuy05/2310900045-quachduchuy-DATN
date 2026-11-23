package com.quachduchuy.controller;

import com.quachduchuy.entity.Booking;
import com.quachduchuy.entity.Homestay;
import com.quachduchuy.entity.Room;
import com.quachduchuy.service.BookingService;
import com.quachduchuy.service.HomestayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Các trang công khai: trang chủ, tìm kiếm, chi tiết homestay. */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final HomestayService homestayService;
    private final BookingService bookingService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("homestays", homestayService.search(null));
        model.addAttribute("cities", homestayService.findAllCities());
        return "index";
    }

    @GetMapping("/homestays")
    public String search(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("homestays", homestayService.search(keyword));
        model.addAttribute("keyword", keyword);
        return "homestay/list";
    }

    @GetMapping("/homestays/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Homestay homestay = homestayService.getActiveHomestay(id);
        List<Room> rooms = homestayService.getRooms(homestay);

        // Với mỗi phòng, lấy các khoảng ngày đã kín để khách biết mà tránh
        Map<Long, List<Booking>> bookedRanges = new HashMap<>();
        for (Room room : rooms) {
            bookedRanges.put(room.getId(), bookingService.getBookedRanges(room.getId()));
        }

        model.addAttribute("homestay", homestay);
        model.addAttribute("rooms", rooms);
        model.addAttribute("bookedRanges", bookedRanges);
        return "homestay/detail";
    }

    @GetMapping("/error/403")
    public String accessDenied() {
        return "error/403";
    }
}
