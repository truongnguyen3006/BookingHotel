package com.example.bookinghotel.ui

enum class PriceFilter {
    ALL,
    UNDER_100,
    AT_LEAST_100
}

enum class RoomSortOption {
    DEFAULT,
    PRICE_LOW_TO_HIGH,
    PRICE_HIGH_TO_LOW
}

data class RoomFilterState(
    val query: String = "",
    val onlyAvailable: Boolean = false,
    val priceFilter: PriceFilter = PriceFilter.ALL,
    val amenity: String? = null,
    val sortOption: RoomSortOption = RoomSortOption.DEFAULT
)
