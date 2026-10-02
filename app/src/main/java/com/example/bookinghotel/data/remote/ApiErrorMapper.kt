package com.example.bookinghotel.data.remote

import com.example.bookinghotel.R
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
    data class Validation(val serverMessage: String?, val details: Map<String, String>, val code: String? = null) : AppError
    data class Conflict(val code: String?, val serverMessage: String?) : AppError
    data class NotFound(val serverMessage: String?) : AppError
    data class Server(val status: Int, val serverMessage: String?) : AppError
    data class Unknown(val cause: Throwable) : AppError
}

fun Throwable.toAppError(): AppError {
    if (this is com.example.bookinghotel.data.auth.StaleSessionException) return AppError.Unauthorized("AUTH_REQUIRED", null)
    if (this is IOException) return AppError.Network(this)
    if (this !is HttpException) return AppError.Unknown(this)

    val parsed = runCatching {
        response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let {
            Gson().fromJson(it, ApiErrorDto::class.java)
        }
    }.getOrNull()

    return when (code()) {
        400 -> AppError.Validation(parsed?.message, parsed?.details.orEmpty(), parsed?.code)
        401 -> AppError.Unauthorized(parsed?.code, parsed?.message)
        403 -> AppError.Forbidden(parsed?.message)
        404 -> AppError.NotFound(parsed?.message)
        409 -> AppError.Conflict(parsed?.code, parsed?.message)
        else -> AppError.Server(code(), parsed?.message)
    }
}

fun AppError.userMessage(strings: com.example.bookinghotel.ui.AppStrings): String = when (this) {
    is AppError.Network -> strings.get(R.string.khong_the_ket_noi_toi_may_chu_vui_long_kiem_tra_ket_noi)
    is AppError.Unauthorized -> when (code) {
        "INVALID_CREDENTIALS" -> strings.get(R.string.email_hoac_mat_khau_khong_ung)
        "INVALID_REFRESH_TOKEN", "EXPIRED_REFRESH_TOKEN", "AUTH_REQUIRED" -> strings.get(R.string.phien_ang_nhap_a_het_han_vui_long_ang_nhap_lai)
        else -> serverMessage ?: strings.get(R.string.ban_can_ang_nhap_e_tiep_tuc)
    }
    is AppError.Forbidden -> serverMessage ?: strings.get(R.string.ban_khong_co_quyen_thuc_hien_thao_tac_nay)
    is AppError.Validation -> if (code == "DEMO_PAYMENT_DISABLED") strings.get(R.string.demo_payment_disabled) else details.values.firstOrNull() ?: serverMessage ?: strings.get(R.string.du_lieu_chua_hop_le)
    is AppError.Conflict -> when (code) {
        "EMAIL_ALREADY_EXISTS" -> strings.get(R.string.email_nay_a_uoc_ang_ky)
        "ROOM_UNAVAILABLE" -> strings.get(R.string.so_phong_vua_thay_oi_tren_may_chu_vui_long_tai_lai_va_t)
        "ROOM_BUSY" -> strings.get(R.string.phong_ang_uoc_nguoi_khac_cap_nhat_vui_long_thu_lai)
        "PAYMENT_IN_PROGRESS" -> strings.get(R.string.payment_in_progress)
        "BOOKING_ALREADY_PAID" -> strings.get(R.string.booking_nay_a_uoc_thanh_toan)
        else -> serverMessage ?: strings.get(R.string.du_lieu_vua_thay_oi_vui_long_thu_lai)
    }
    is AppError.NotFound -> serverMessage ?: strings.get(R.string.khong_tim_thay_du_lieu_yeu_cau)
    is AppError.Server -> serverMessage ?: strings.get(R.string.may_chu_gap_loi_vui_long_thu_lai_sau, status)
    is AppError.Unknown -> cause.message ?: strings.get(R.string.a_xay_ra_loi_vui_long_thu_lai)
}
