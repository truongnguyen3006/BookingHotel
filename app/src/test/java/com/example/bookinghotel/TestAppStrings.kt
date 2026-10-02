package com.example.bookinghotel

import com.example.bookinghotel.ui.AppStrings

object TestAppStrings : AppStrings {
    override fun get(id: Int, vararg args: Any): String = when (id) {
        R.string.so_luong_phong_khong_hop_le -> "Số lượng phòng không hợp lệ."
        R.string.ngay_tra_phong_phai_sau_ngay_nhan_phong -> "Ngày trả phòng phải sau ngày nhận phòng."
        R.string.so_khach_toi_thieu_la_1 -> "Số khách tối thiểu là 1."
        R.string.khong_tim_thay_booking_e_thanh_toan -> "Không tìm thấy booking để thanh toán."
        R.string.khong_the_ket_noi_toi_may_chu_vui_long_kiem_tra_ket_noi -> "Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối và thử lại."
        R.string.booking_a_uoc_thanh_toan -> "Booking đã được thanh toán."
        R.string.ang_kiem_tra_trang_thai_giao_dich_voi_backend -> "Đang kiểm tra trạng thái giao dịch..."
        R.string.vnpay_chua_gui_xac_nhan_cuoi_cung_hay_oi_vai_giay_roi_k -> "VNPAY chưa gửi xác nhận cuối cùng. Hãy đợi vài giây rồi kiểm tra lại."
        R.string.giao_dich_ang_uoc_xu_ly_kiem_tra_trang_thai_e_lay_ket_q -> "Giao dịch đang được xử lý. Kiểm tra trạng thái để lấy kết quả mới nhất."
        R.string.khong_tim_thay_booking_trong_tai_khoan_hien_tai -> "Không tìm thấy booking trong tài khoản hiện tại."
        R.string.phien_ang_nhap_a_het_han_vui_long_ang_nhap_lai -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        else -> "test message $id"
    }
}
