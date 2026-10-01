package com.example.bookinghotel.backend.controller;

import com.example.bookinghotel.backend.api.dto.AdminBookingResponse;
import com.example.bookinghotel.backend.api.dto.AdminDashboardResponse;
import com.example.bookinghotel.backend.api.dto.AdminRoomUpdateRequest;
import com.example.bookinghotel.backend.api.dto.RoomResponse;
import com.example.bookinghotel.backend.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse getDashboard() {
        return adminService.getDashboard();
    }

    @GetMapping("/rooms")
    public List<RoomResponse> getRooms() {
        return adminService.getRooms();
    }

    @PatchMapping("/rooms/{roomId}")
    public RoomResponse updateRoom(
            @PathVariable int roomId,
            @Valid @RequestBody AdminRoomUpdateRequest request
    ) {
        return adminService.updateRoom(roomId, request);
    }

    @GetMapping("/bookings")
    public List<AdminBookingResponse> getBookings() {
        return adminService.getBookings();
    }
}
