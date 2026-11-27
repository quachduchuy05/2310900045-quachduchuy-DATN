package com.quachduchuy;

import com.quachduchuy.dto.BookingForm;
import com.quachduchuy.entity.Booking;
import com.quachduchuy.entity.Role;
import com.quachduchuy.entity.Room;
import com.quachduchuy.entity.User;
import com.quachduchuy.repository.*;
import com.quachduchuy.service.BookingService;
import com.quachduchuy.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Kiểm thử trang web: hiển thị, phân quyền và kiểm tra dữ liệu. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class WebTests {

    @Autowired MockMvc mvc;
    @Autowired BookingService bookingService;
    @Autowired PaymentService paymentService;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired HomestayRepository homestayRepository;

    Booking booking;
    Long roomId;
    Long homestayId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        Room room = roomRepository.findAll().get(0);
        roomId = room.getId();
        homestayId = homestayRepository.findAll().get(0).getId();
        BookingForm f = new BookingForm();
        f.setRoomId(roomId);
        f.setCheckIn(LocalDate.now());
        f.setCheckOut(LocalDate.now().plusDays(2));
        booking = bookingService.createBooking(find("user@staygo.vn"), f);
    }

    private User find(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private static UserRequestPostProcessor admin() {
        return user("admin@staygo.vn").roles("ADMIN");
    }

    private static UserRequestPostProcessor host() {
        return user("host@staygo.vn").roles("HOST");
    }

    private static UserRequestPostProcessor guest() {
        return user("user@staygo.vn").roles("USER");
    }

    @Test
    void publicPagesRender() throws Exception {
        for (String url : new String[]{"/", "/homestays", "/homestays?keyword=Đà", "/homestays/" + homestayId,
                "/login", "/register"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    void privatePagesRenderWithData() throws Exception {
        for (String url : new String[]{"/bookings", "/bookings/" + booking.getId(), "/ai"}) {
            mvc.perform(get(url).with(guest())).andExpect(status().isOk());
        }
        for (String url : new String[]{"/host", "/host/homestays/new", "/host/homestays/" + homestayId + "/edit",
                "/host/homestays/" + homestayId + "/rooms", "/host/homestays/" + homestayId + "/rooms/new",
                "/host/rooms/" + roomId + "/edit"}) {
            mvc.perform(get(url).with(host())).andExpect(status().isOk());
        }
        mvc.perform(get("/admin").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"CANCELLED\"")));
    }

    @Test
    void aiPlanWorksWithoutApiKey() throws Exception {
        mvc.perform(post("/ai/plan").with(guest()).with(csrf())
                        .param("destination", "Đà Lạt").param("days", "3").param("guests", "2")
                        .param("budget", "3000000"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("suggestion"));
    }

    @Test
    void rolesAreEnforced() throws Exception {
        mvc.perform(get("/admin").with(guest())).andExpect(status().isForbidden());
        mvc.perform(get("/host").with(guest())).andExpect(status().isForbidden());
        mvc.perform(get("/bookings")).andExpect(status().is3xxRedirection());
    }

    @Test
    void otherUsersCannotTouchMyPayment() throws Exception {
        String url = paymentService.createPaymentUrl(find("user@staygo.vn"), booking.getId(), "127.0.0.1");
        String txnRef = url.substring(url.indexOf('=') + 1);

        mvc.perform(get(url).with(guest())).andExpect(status().isOk());
        mvc.perform(post("/payment/demo-gateway").with(host()).with(csrf())
                        .param("txnRef", txnRef).param("success", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vnpayCallbacksWorkWithoutLogin() throws Exception {
        String url = paymentService.createPaymentUrl(find("user@staygo.vn"), booking.getId(), "127.0.0.1");
        String query = paymentService.buildDemoReturnQuery(
                find("user@staygo.vn"), url.substring(url.indexOf('=') + 1), true);
        java.net.URI ipn = java.net.URI.create("/payment/vnpay-ipn?" + query);

        mvc.perform(get(ipn)).andExpect(jsonPath("$.RspCode").value("00"));
        mvc.perform(get(ipn)).andExpect(jsonPath("$.RspCode").value("02"));
        mvc.perform(get(java.net.URI.create("/payment/vnpay-return?" + query))).andExpect(status().isOk());
        mvc.perform(get("/payment/vnpay-ipn")).andExpect(jsonPath("$.RspCode").value("97"));
    }

    @Test
    void badInputGivesFriendlyResponseInsteadOfServerError() throws Exception {
        mvc.perform(post("/bookings").with(guest()).with(csrf())).andExpect(status().isBadRequest());

        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "x".repeat(101)).param("email", "a@b.vn").param("phone", "abc")
                        .param("password", "123456").param("confirmPassword", "123456"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "fullName", "phone"));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Trùng").param("email", "USER@staygo.vn")
                        .param("password", "123456").param("confirmPassword", "123456"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));

        mvc.perform(formLogin().userParameter("email").user("email", "User@Staygo.vn").password("123456"))
                .andExpect(authenticated().withUsername("user@staygo.vn"));
    }

    @Test
    void adminCannotChangeOwnRole() throws Exception {
        Long adminId = find("admin@staygo.vn").getId();
        mvc.perform(post("/admin/users/" + adminId + "/role").with(admin()).with(csrf()).param("role", "USER"))
                .andExpect(status().isBadRequest());
        assertThat(find("admin@staygo.vn").getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void homestayWithBookingsCannotBeDeleted() throws Exception {
        Long bookedHomestay = homestayRepository.findAll().stream()
                .map(com.quachduchuy.entity.Homestay::getId)
                .filter(id -> id.equals(homestayId)).findFirst().orElseThrow();
        mvc.perform(post("/host/homestays/" + bookedHomestay + "/delete").with(host()).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/host/rooms/" + roomId + "/delete").with(host()).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        assertThat(roomRepository.existsById(roomId)).isTrue();
    }
}
