package com.example.bookinghotel.backend.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "rooms")
public class RoomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "image_key", nullable = false, length = 100)
    private String imageKey;

    @Column(name = "type_key", nullable = false, unique = true, length = 100)
    private String typeKey;

    @Column(name = "price_per_night", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_amenities", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "amenity", nullable = false, length = 120)
    private Set<String> amenities = new LinkedHashSet<>();

    @Column(name = "available_rooms", nullable = false)
    private int availableRooms;

    protected RoomEntity() {}

    public RoomEntity(String imageKey, String typeKey, BigDecimal pricePerNight, Set<String> amenities, int availableRooms) {
        this.imageKey = imageKey;
        this.typeKey = typeKey;
        this.pricePerNight = pricePerNight;
        this.amenities = new LinkedHashSet<>(amenities);
        this.availableRooms = availableRooms;
    }

    public Integer getId() { return id; }
    public String getImageKey() { return imageKey; }
    public String getTypeKey() { return typeKey; }
    public BigDecimal getPricePerNight() { return pricePerNight; }
    public void setPricePerNight(BigDecimal pricePerNight) { this.pricePerNight = pricePerNight; }
    public Set<String> getAmenities() { return amenities; }
    public int getAvailableRooms() { return availableRooms; }
    public void setAvailableRooms(int availableRooms) { this.availableRooms = availableRooms; }
}
