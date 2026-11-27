package com.quachduchuy.controller;

import com.quachduchuy.dto.SearchForm;
import com.quachduchuy.exception.BusinessException;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Các trang công khai: trang chủ, tìm kiếm, chi tiết homestay. */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final HomestayService homestayService;
    private final BookingService bookingService;

    @GetMapping("/")
    public String home(Model model) {
        List<Homestay> homestays = homestayService.findLatest();
        model.addAttribute("homestays", homestays);
        // Mỗi điểm đến lấy các homestay của thành phố đó (ảnh đại diện + số lượng)
        model.addAttribute("destinations", homestays.stream()
                .collect(Collectors.groupingBy(Homestay::getCity, LinkedHashMap::new, Collectors.toList())));
        model.addAttribute("cities", homestayService.findAllCities());
        return "index";
    }

    @GetMapping("/homestays")
    public String search(@Valid @ModelAttribute("search") SearchForm search, BindingResult result, Model model) {
        List<Homestay> homestays = List.of();
        if (result.hasErrors()) {
            model.addAttribute("searchError", "Bộ lọc không hợp lệ, vui lòng kiểm tra ngày, số khách và giá");
        } else {
            try {
                homestays = homestayService.search(search);
            } catch (BusinessException e) {
                model.addAttribute("searchError", e.getMessage());
            }
        }
        model.addAttribute("homestays", homestays);
        model.addAttribute("cities", homestayService.findAllCities());
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
