package com.example.bookinghotel.data.remote

import com.google.gson.Gson
import java.io.IOException
import retrofit2.HttpException

data class ApiErrorDto(
    val code: String? = null,
    val message: String? = null,
    val details: Map<String, String>? = null
)

sealed interface AppError {
    data class Network(val cause: Throwable) : AppError
    data class Unauthorized(val code: String?, val serverMessage: String?) : AppError
    data class Forbidden(val serverMessage: String?) : AppError
    data class Validation(val serverMessage: String?, val details: Map<String, String>) : AppError
    data class Conflict(val code: String?, val serverMessage: String?) : AppError
    data class NotFound(val serverMessage: String?) : AppError
    data class Server(val status: Int, val serverMessage: String?) : AppError
    data class Unknown(val cause: Throwable) : AppError
}

fun Throwable.toAppError(): AppError {
    if (this is IOException) return AppError.Network(this)
    if (this !is HttpException) return AppError.Unknown(this)

    val parsed = runCatching {
        response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let {
            Gson().fromJson(it, ApiErrorDto::class.java)
        }
    }.getOrNull()

    return when (code()) {
        400 -> AppError.Validation(parsed?.message, parsed?.details.orEmpty())
        401 -> AppError.Unauthorized(parsed?.code, parsed?.message)
        403 -> AppError.Forbidden(parsed?.message)
        404 -> AppError.NotFound(parsed?.message)
        409 -> AppError.Conflict(parsed?.code, parsed?.message)
        else -> AppError.Server(code(), parsed?.message)
    }
}

fun AppError.userMessage(): String = when (this) {
    is AppError.Network -> "Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối và thử lại."
    is AppError.Unauthorized -> when (code) {
        "INVALID_CREDENTIALS" -> "Email hoặc mật khẩu không đúng."
        "INVALID_REFRESH_TOKEN", "EXPIRED_REFRESH_TOKEN", "AUTH_REQUIRED" -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        else -> serverMessage ?: "Bạn cần đăng nhập để tiếp tục."
    }
    is AppError.Forbidden -> serverMessage ?: "Bạn không có quyền thực hiện thao tác này."
    is AppError.Validation -> details.values.firstOrNull() ?: serverMessage ?: "Dữ liệu chưa hợp lệ."
    is AppError.Conflict -> when (code) {
        "EMAIL_ALREADY_EXISTS" -> "Email này đã được đăng ký."
        "ROOM_UNAVAILABLE" -> "Số phòng vừa thay đổi trên máy chủ. Vui lòng tải lại và thử lại."
        "ROOM_BUSY" -> "Phòng đang được người khác cập nhật. Vui lòng thử lại."
        "BOOKING_ALREADY_PAID" -> "Booking này đã được thanh toán."
        else -> serverMessage ?: "Dữ liệu vừa thay đổi. Vui lòng thử lại."
    }
    is AppError.NotFound -> serverMessage ?: "Không tìm thấy dữ liệu yêu cầu."
    is AppError.Server -> serverMessage ?: "Máy chủ gặp lỗi (${status}). Vui lòng thử lại sau."
    is AppError.Unknown -> cause.message ?: "Đã xảy ra lỗi. Vui lòng thử lại."
}
