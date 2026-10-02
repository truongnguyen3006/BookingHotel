package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.AdminBooking
import com.example.bookinghotel.data.AdminDashboard
import com.example.bookinghotel.data.AdminPayment
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.remote.AdminApiService
import com.example.bookinghotel.data.remote.dto.AdminRoomUpdateRequestDto
import com.example.bookinghotel.data.remote.toDomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitAdminRepository @Inject constructor(
    private val api: AdminApiService
) : AdminRepository {
    override suspend fun getDashboard(): Result<AdminDashboard> = runCatching {
        api.getDashboard().let {
            AdminDashboard(
                totalBookings = it.totalBookings,
                paidBookings = it.paidBookings,
                totalRevenue = it.totalRevenue,
                roomTypes = it.roomTypes,
                availableRoomInventory = it.availableRoomInventory
            )
        }
    }

    override suspend fun getRooms(): Result<List<Room>> = runCatching {
        api.getRooms().map { it.toDomain() }
    }

    override suspend fun updateRoom(
        roomId: Int,
        pricePerNight: Long,
        availableRooms: Int
    ): Result<Room> = runCatching {
        api.updateRoom(
            roomId,
            AdminRoomUpdateRequestDto(
                pricePerNight = pricePerNight,
                availableRooms = availableRooms
            )
        ).toDomain()
    }

    override suspend fun getBookings(): Result<List<AdminBooking>> = runCatching {
        api.getBookings().map { dto ->
            AdminBooking(
                bookingId = dto.bookingId,
                room = dto.room.toDomain(),
                quantity = dto.quantity,
                totalPrice = dto.totalPrice,
                status = dto.status,
                checkInDate = dto.checkInDate,
                checkOutDate = dto.checkOutDate,
                guests = dto.guests,
                nights = dto.nights,
                createdAt = dto.createdAt,
                userId = dto.userId,
                userEmail = dto.userEmail,
                userDisplayName = dto.userDisplayName,
                payment = dto.payment?.let { payment ->
                    AdminPayment(
                        status = payment.status,
                        method = payment.method,
                        transactionId = payment.transactionId,
                        paidAt = payment.paidAt
                    )
                }
            )
        }
    }
}
