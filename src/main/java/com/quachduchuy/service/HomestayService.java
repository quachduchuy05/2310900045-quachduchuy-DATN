package com.quachduchuy.service;

import com.quachduchuy.dto.HomestayForm;
import com.quachduchuy.dto.RoomForm;
import com.quachduchuy.entity.Homestay;
import com.quachduchuy.entity.Role;
import com.quachduchuy.entity.Room;
import com.quachduchuy.entity.User;
import com.quachduchuy.exception.BusinessException;
import com.quachduchuy.repository.HomestayRepository;
import com.quachduchuy.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Nghiệp vụ quản lý homestay và phòng. */
@Service
@RequiredArgsConstructor
public class HomestayService {

    private final HomestayRepository homestayRepository;
    private final RoomRepository roomRepository;

    // ===================== DÀNH CHO KHÁCH =====================

    public List<Homestay> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return homestayRepository.findByActiveTrueOrderByCreatedAtDesc();
        }
        return homestayRepository.search(keyword.trim());
    }

    public List<String> findAllCities() {
        return homestayRepository.findAllCities();
    }

    public Homestay getActiveHomestay(Long id) {
        Homestay h = getHomestay(id);
        if (!h.isActive()) {
            throw new BusinessException("Homestay này tạm thời không hoạt động");
        }
        return h;
    }

    public Homestay getHomestay(Long id) {
        return homestayRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy homestay"));
    }

    public Room getRoom(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng"));
    }

    public List<Room> getRooms(Homestay homestay) {
        return roomRepository.findByHomestay(homestay);
    }

    // ===================== DÀNH CHO HOST =====================

    public List<Homestay> findByHost(User host) {
        return homestayRepository.findByHost(host);
    }

    @Transactional
    public Homestay createHomestay(User host, HomestayForm form) {
        Homestay h = new Homestay();
        h.setHost(host);
        copyForm(form, h);
        return homestayRepository.save(h);
    }

    @Transactional
    public void updateHomestay(User currentUser, Long id, HomestayForm form) {
        Homestay h = getOwnedHomestay(currentUser, id);
        copyForm(form, h);
    }

    @Transactional
    public void deleteHomestay(User currentUser, Long id) {
        Homestay h = getOwnedHomestay(currentUser, id);
        homestayRepository.delete(h);
    }

    @Transactional
    public Room addRoom(User currentUser, Long homestayId, RoomForm form) {
        Homestay h = getOwnedHomestay(currentUser, homestayId);
        Room r = new Room();
        r.setHomestay(h);
        copyForm(form, r);
        return roomRepository.save(r);
    }

    @Transactional
    public void updateRoom(User currentUser, Long roomId, RoomForm form) {
        Room r = getOwnedRoom(currentUser, roomId);
        copyForm(form, r);
    }

    @Transactional
    public void deleteRoom(User currentUser, Long roomId) {
        Room r = getOwnedRoom(currentUser, roomId);
        roomRepository.delete(r);
    }

    /** Lấy homestay và kiểm tra người đang thao tác có phải chủ nhà (hoặc Admin) không. */
    public Homestay getOwnedHomestay(User currentUser, Long id) {
        Homestay h = getHomestay(id);
        boolean isOwner = h.getHost().getId().equals(currentUser.getId());
        if (!isOwner && currentUser.getRole() != Role.ADMIN) {
            throw new BusinessException("Bạn không có quyền sửa homestay này");
        }
        return h;
    }

    public Room getOwnedRoom(User currentUser, Long roomId) {
        Room r = getRoom(roomId);
        getOwnedHomestay(currentUser, r.getHomestay().getId()); // kiểm tra quyền
        return r;
    }

    // ===================== DÀNH CHO ADMIN =====================

    public List<Homestay> findAll() {
        return homestayRepository.findAll();
    }

    @Transactional
    public void toggleActive(Long id) {
        Homestay h = getHomestay(id);
        h.setActive(!h.isActive());
    }

    // ===================== CHUYỂN DỮ LIỆU FORM ↔ ENTITY =====================

    public HomestayForm toForm(Homestay h) {
        HomestayForm f = new HomestayForm();
        f.setName(h.getName());
        f.setCity(h.getCity());
        f.setAddress(h.getAddress());
        f.setDescription(h.getDescription());
        f.setImageUrl(h.getImageUrl());
        f.setAmenities(h.getAmenities());
        return f;
    }

    public RoomForm toForm(Room r) {
        RoomForm f = new RoomForm();
        f.setName(r.getName());
        f.setPricePerNight(r.getPricePerNight());
        f.setCapacity(r.getCapacity());
        f.setDescription(r.getDescription());
        f.setImageUrl(r.getImageUrl());
        f.setAvailable(r.isAvailable());
        return f;
    }

    private void copyForm(HomestayForm f, Homestay h) {
        h.setName(f.getName());
        h.setCity(f.getCity());
        h.setAddress(f.getAddress());
        h.setDescription(f.getDescription());
        h.setImageUrl(f.getImageUrl());
        h.setAmenities(f.getAmenities());
    }

    private void copyForm(RoomForm f, Room r) {
        r.setName(f.getName());
        r.setPricePerNight(f.getPricePerNight());
        r.setCapacity(f.getCapacity());
        r.setDescription(f.getDescription());
        r.setImageUrl(f.getImageUrl());
        r.setAvailable(f.isAvailable());
    }
}
