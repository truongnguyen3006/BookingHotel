package com.example.bookinghotel.backend.controller;

import com.example.bookinghotel.backend.api.dto.RoomResponse;
import com.example.bookinghotel.backend.service.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomResponse> getRooms() {
        return roomService.getRooms();
    }

    @GetMapping("/{roomId}")
    public RoomResponse getRoom(@PathVariable int roomId) {
        return roomService.getRoom(roomId);
    }
}
