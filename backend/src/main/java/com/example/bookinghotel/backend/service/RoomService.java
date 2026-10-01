package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.api.dto.RoomResponse;
import com.example.bookinghotel.backend.domain.RoomEntity;
import com.example.bookinghotel.backend.exception.NotFoundException;
import com.example.bookinghotel.backend.repository.RoomJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class RoomService {
    private final RoomJpaRepository roomRepository;

    public RoomService(RoomJpaRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms() {
        return roomRepository.findAll().stream()
                .sorted(Comparator.comparing(RoomEntity::getId))
                .map(RoomService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(int roomId) {
        return toResponse(findRoom(roomId));
    }

    public RoomEntity findRoom(int roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
    }

    public static RoomResponse toResponse(RoomEntity room) {
        return new RoomResponse(
                room.getId(),
                room.getImageKey(),
                room.getTypeKey(),
                room.getPricePerNight().doubleValue(),
                room.getAmenities().stream().sorted().toList(),
                room.getAvailableRooms()
        );
    }
}
