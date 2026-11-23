package com.quachduchuy.config;

import com.quachduchuy.entity.Homestay;
import com.quachduchuy.entity.Role;
import com.quachduchuy.entity.Room;
import com.quachduchuy.entity.User;
import com.quachduchuy.repository.HomestayRepository;
import com.quachduchuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Tạo DỮ LIỆU MẪU lần đầu chạy (khi bảng users còn trống) để demo ngay được.
 * Tài khoản mẫu (mật khẩu đều là 123456):
 *   admin@staygo.vn  - ADMIN
 *   host@staygo.vn   - HOST
 *   user@staygo.vn   - USER
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final HomestayRepository homestayRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return; // đã có dữ liệu thì không tạo lại
        }

        createUser("admin@staygo.vn", "Quản trị viên", Role.ADMIN);
        User host = createUser("host@staygo.vn", "Nguyễn Văn Chủ", Role.HOST);
        createUser("user@staygo.vn", "Trần Thị Khách", Role.USER);

        Homestay dalat = createHomestay(host, "Mây Lang Thang Homestay", "Đà Lạt",
                "12 Đường Trần Hưng Đạo, Phường 10, Đà Lạt",
                "Homestay gỗ thông giữa đồi, săn mây buổi sáng, cách chợ Đà Lạt 2km.",
                "Wifi, Bữa sáng, Chỗ đậu xe, Lò sưởi",
                "https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800");
        addRoom(dalat, "Phòng đôi view đồi thông", 550000, 2,
                "https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800");
        addRoom(dalat, "Phòng gia đình gác mái", 900000, 4,
                "https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800");

        Homestay hoian = createHomestay(host, "Phố Cổ Lantern House", "Hội An",
                "45 Nguyễn Thị Minh Khai, Hội An, Quảng Nam",
                "Nhà cổ cải tạo, đi bộ 5 phút tới phố đèn lồng, miễn phí xe đạp.",
                "Wifi, Xe đạp miễn phí, Bể bơi, Bữa sáng",
                "https://images.unsplash.com/photo-1582719508461-905c673771fd?w=800");
        addRoom(hoian, "Phòng tiêu chuẩn sân vườn", 450000, 2,
                "https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800");
        addRoom(hoian, "Phòng deluxe ban công", 750000, 3,
                "https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800");

        Homestay sapa = createHomestay(host, "Sapa Cloud Valley", "Sa Pa",
                "Bản Cát Cát, Sa Pa, Lào Cai",
                "Nhà sàn nhìn thung lũng Mường Hoa, có tour trekking bản làng.",
                "Wifi, Bữa sáng, Tour trekking, Lò sưởi",
                "https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?w=800");
        addRoom(sapa, "Phòng dorm 6 giường", 200000, 1,
                "https://images.unsplash.com/photo-1555854877-bab0e564b8d5?w=800");
        addRoom(sapa, "Bungalow view thung lũng", 1200000, 2,
                "https://images.unsplash.com/photo-1602002418082-a4443e081dd1?w=800");

        homestayRepository.save(dalat);
        homestayRepository.save(hoian);
        homestayRepository.save(sapa);
    }

    private User createUser(String email, String name, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setPassword(passwordEncoder.encode("123456"));
        u.setPhone("0900000000");
        u.setRole(role);
        return userRepository.save(u);
    }

    private Homestay createHomestay(User host, String name, String city, String address,
                                    String desc, String amenities, String image) {
        Homestay h = new Homestay();
        h.setHost(host);
        h.setName(name);
        h.setCity(city);
        h.setAddress(address);
        h.setDescription(desc);
        h.setAmenities(amenities);
        h.setImageUrl(image);
        return h;
    }

    private void addRoom(Homestay h, String name, long price, int capacity, String image) {
        Room r = new Room();
        r.setHomestay(h);
        r.setName(name);
        r.setPricePerNight(BigDecimal.valueOf(price));
        r.setCapacity(capacity);
        r.setImageUrl(image);
        r.setDescription("Phòng sạch sẽ, đầy đủ tiện nghi, có phòng tắm riêng.");
        h.getRooms().add(r);
    }
}
