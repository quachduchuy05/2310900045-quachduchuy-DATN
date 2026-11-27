package com.quachduchuy;

import com.quachduchuy.dto.BookingForm;
import com.quachduchuy.entity.*;
import com.quachduchuy.repository.*;
import com.quachduchuy.service.BookingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Kiểm thử: đánh giá, hồ sơ, tìm kiếm, tải ảnh, thống kê và khóa tài khoản. */
@SpringBootTest(properties = "app.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FeatureTests {

    @Autowired MockMvc mvc;
    @Autowired BookingService bookingService;
    @Autowired com.quachduchuy.service.PaymentService paymentService;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired HomestayRepository homestayRepository;
    @Autowired PasswordEncoder passwordEncoder;

    Room room;
    Long homestayId;

    @BeforeEach
    @AfterEach
    void reset() {
        reviewRepository.deleteAll();
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        for (User u : userRepository.findAll()) {
            u.setEnabled(true);
            u.setPassword(passwordEncoder.encode("123456"));
            if (u.getEmail().equals("host@staygo.vn")) u.setRole(Role.HOST);
            userRepository.save(u);
        }
        room = roomRepository.findAll().get(0);
        homestayId = room.getHomestay().getId();
    }

    private static RequestPostProcessor guest() {
        return user("user@staygo.vn").roles("USER");
    }

    private static RequestPostProcessor host() {
        return user("host@staygo.vn").roles("HOST");
    }

    private static RequestPostProcessor admin() {
        return user("admin@staygo.vn").roles("ADMIN");
    }

    private User find(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Booking book(int fromDay, int toDay) {
        BookingForm f = new BookingForm();
        f.setRoomId(room.getId());
        f.setCheckIn(LocalDate.now().plusDays(fromDay));
        f.setCheckOut(LocalDate.now().plusDays(toDay));
        return bookingService.createBooking(find("user@staygo.vn"), f);
    }

    private Booking completedBooking() {
        Booking b = bookingRepository.findById(book(1, 3).getId()).orElseThrow();
        b.setStatus(BookingStatus.PAID);
        b.setCheckIn(LocalDate.now().minusDays(3));
        b.setCheckOut(LocalDate.now().minusDays(1));
        bookingRepository.save(b);
        bookingService.completeFinishedBookings();
        return bookingRepository.findById(b.getId()).orElseThrow();
    }

    @Test
    void guestReviewsOnceAfterStayAndAdminCanDelete() throws Exception {
        Booking b = completedBooking();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        String reviewUrl = "/bookings/" + b.getId() + "/review";

        mvc.perform(get("/bookings/" + b.getId()).with(guest()))
                .andExpect(content().string(containsString("Gửi đánh giá")));
        mvc.perform(post(reviewUrl).with(host()).with(csrf()).param("rating", "1"))
                .andExpect(status().isBadRequest()); // không phải đơn của mình
        mvc.perform(post(reviewUrl).with(guest()).with(csrf()).param("rating", "9"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post(reviewUrl).with(guest()).with(csrf()).param("rating", "4").param("comment", "Phòng sạch"))
                .andExpect(flash().attributeExists("success"));
        mvc.perform(post(reviewUrl).with(guest()).with(csrf()).param("rating", "5"))
                .andExpect(status().isBadRequest()); // đã đánh giá rồi

        mvc.perform(get("/bookings/" + b.getId()).with(guest()))
                .andExpect(content().string(containsString("Đánh giá của bạn")));
        mvc.perform(get("/homestays/" + homestayId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phòng sạch")));
        mvc.perform(get("/homestays").param("sort", "rating"))
                .andExpect(content().string(containsString("1 đánh giá")));

        Long reviewId = reviewRepository.findAll().get(0).getId();
        mvc.perform(post("/admin/reviews/" + reviewId + "/delete").with(guest()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/reviews/" + reviewId + "/delete").with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void unfinishedBookingCannotBeReviewed() throws Exception {
        Booking b = book(1, 3);
        mvc.perform(post("/bookings/" + b.getId() + "/review").with(guest()).with(csrf()).param("rating", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchFiltersByAvailabilityGuestsAndPrice() throws Exception {
        book(10, 12);
        String from = LocalDate.now().plusDays(10).toString();
        String to = LocalDate.now().plusDays(11).toString();
        String name = homestayRepository.findById(homestayId).orElseThrow().getName();

        // Đặt kín mọi phòng của homestay đầu thì nó biến mất khỏi kết quả theo ngày đó
        for (Room other : roomRepository.findByHomestay(room.getHomestay())) {
            if (!other.getId().equals(room.getId())) {
                BookingForm f = new BookingForm();
                f.setRoomId(other.getId());
                f.setCheckIn(LocalDate.now().plusDays(10));
                f.setCheckOut(LocalDate.now().plusDays(12));
                bookingService.createBooking(find("user@staygo.vn"), f);
            }
        }
        mvc.perform(get("/homestays").param("checkIn", from).param("checkOut", to))
                .andExpect(model().attribute("homestays", org.hamcrest.Matchers.hasSize(2)));
        mvc.perform(get("/homestays").param("keyword", name))
                .andExpect(model().attribute("homestays", contains(hasProperty("id", org.hamcrest.Matchers.is(homestayId)))));
        mvc.perform(get("/homestays").param("maxPrice", "250000"))
                .andExpect(model().attribute("homestays", org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(get("/homestays").param("guests", "4"))
                .andExpect(model().attribute("homestays", org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(get("/homestays").param("sort", "priceDesc"))
                .andExpect(status().isOk());
        mvc.perform(get("/homestays").param("checkIn", from))
                .andExpect(status().isOk()).andExpect(model().attributeExists("searchError"));
        mvc.perform(get("/homestays").param("guests", "abc"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("searchError"));
    }

    @Test
    void userUpdatesProfileAndPassword() throws Exception {
        mvc.perform(get("/profile").with(guest())).andExpect(status().isOk());
        mvc.perform(post("/profile").with(guest()).with(csrf()).param("fullName", "Tên Mới").param("phone", "0911222333"))
                .andExpect(redirectedUrl("/profile"));
        assertThat(find("user@staygo.vn").getFullName()).isEqualTo("Tên Mới");
        mvc.perform(post("/profile").with(guest()).with(csrf()).param("fullName", "").param("phone", "x"))
                .andExpect(model().attributeHasFieldErrors("profileForm", "fullName", "phone"));

        mvc.perform(post("/profile/password").with(guest()).with(csrf())
                        .param("currentPassword", "sai-mat-khau")
                        .param("newPassword", "abcdef").param("confirmPassword", "abcdef"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        mvc.perform(post("/profile/password").with(guest()).with(csrf())
                        .param("currentPassword", "123456")
                        .param("newPassword", "abcdef").param("confirmPassword", "abcdef"))
                .andExpect(redirectedUrl("/profile"));
        assertThat(passwordEncoder.matches("abcdef", find("user@staygo.vn").getPassword())).isTrue();
    }

    @Test
    void lockingAndRoleChangeApplyToOpenSessions() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc
                .perform(formLogin().userParameter("email").user("email", "host@staygo.vn").password("123456"))
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/host").session(session)).andExpect(status().isOk());

        User h = find("host@staygo.vn");
        h.setRole(Role.USER);
        userRepository.save(h);
        mvc.perform(get("/host").session(session)).andExpect(status().isForbidden());

        h = find("host@staygo.vn");
        h.setEnabled(false);
        userRepository.save(h);
        mvc.perform(get("/bookings").session(session)).andExpect(redirectedUrl("/login?error"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void hostUploadsImageAndSeesRevenue() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
        mvc.perform(multipart("/host/homestays/new")
                        .file(new MockMultipartFile("imageFile", "a.png", "image/png", png))
                        .param("name", "Nhà thử ảnh").param("city", "Huế").param("address", "1 Lê Lợi")
                        .with(host()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Homestay created = homestayRepository.findAll().stream()
                .filter(h -> h.getName().equals("Nhà thử ảnh")).findFirst().orElseThrow();
        try {
            assertThat(created.getImageUrl()).startsWith("/uploads/").endsWith(".png");
            mvc.perform(get(created.getImageUrl())).andExpect(status().isOk()).andExpect(content().bytes(png));
            // trang sửa phải hiển thị được đường dẫn ảnh đã tải lên
            mvc.perform(get("/host/homestays/" + created.getId() + "/edit").with(host()))
                    .andExpect(content().string(containsString(created.getImageUrl())));
        } finally {
            homestayRepository.delete(created);
        }

        mvc.perform(multipart("/host/homestays/new")
                        .file(new MockMultipartFile("imageFile", "x.png", "image/png", "<html>".getBytes()))
                        .param("name", "Giả ảnh").param("city", "Huế").param("address", "1 Lê Lợi")
                        .with(host()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "imageFile"));

        completedBooking();
        mvc.perform(get("/host").with(host()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("revenue", org.hamcrest.Matchers.greaterThan(java.math.BigDecimal.ZERO)));
    }

    @Test
    void confirmPageShowsTotalWithoutCreatingBooking() throws Exception {
        String from = LocalDate.now().plusDays(40).toString();
        String to = LocalDate.now().plusDays(43).toString();
        mvc.perform(get("/bookings/confirm").with(guest())
                        .param("roomId", room.getId().toString())
                        .param("checkIn", from).param("checkOut", to).param("guests", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/confirm"))
                .andExpect(content().string(containsString("1.650.000"))); // 550.000 x 3 đêm
        assertThat(bookingRepository.count()).isZero();

        // ngày sai hoặc phòng đã kín thì quay lại trang homestay kèm thông báo
        mvc.perform(get("/bookings/confirm").with(guest())
                        .param("roomId", room.getId().toString())
                        .param("checkIn", to).param("checkOut", from))
                .andExpect(redirectedUrl("/homestays/" + homestayId))
                .andExpect(flash().attributeExists("error"));
        book(40, 43);
        mvc.perform(get("/bookings/confirm").with(guest())
                        .param("roomId", room.getId().toString())
                        .param("checkIn", from).param("checkOut", to))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(get("/bookings/confirm").with(guest())).andExpect(status().isBadRequest());
    }

    @Test
    void paymentHistoryIsScopedByRole() throws Exception {
        Booking b = book(50, 52);
        String url = paymentService.createPaymentUrl(find("user@staygo.vn"), b.getId(), "127.0.0.1");
        String txnRef = url.substring(url.indexOf('=') + 1);

        for (String page : new String[]{"/payments", "/bookings/" + b.getId()}) {
            mvc.perform(get(page).with(guest()))
                    .andExpect(status().isOk()).andExpect(content().string(containsString(txnRef)));
        }
        mvc.perform(get("/host/payments").with(host()))
                .andExpect(status().isOk()).andExpect(content().string(containsString(txnRef)));
        mvc.perform(get("/admin/payments").with(admin()))
                .andExpect(status().isOk()).andExpect(content().string(containsString(txnRef)));
        // người khác không thấy giao dịch này trong lịch sử của họ
        mvc.perform(get("/payments").with(host()))
                .andExpect(model().attribute("payments", org.hamcrest.Matchers.empty()));
        mvc.perform(get("/payments").with(guest()).param("status", "SUCCESS"))
                .andExpect(model().attribute("payments", org.hamcrest.Matchers.empty()))
                .andExpect(model().attribute("totalCount", 1));
        mvc.perform(get("/admin/payments").with(host())).andExpect(status().isForbidden());
        mvc.perform(get("/host/payments").with(guest())).andExpect(status().isForbidden());
    }
}
