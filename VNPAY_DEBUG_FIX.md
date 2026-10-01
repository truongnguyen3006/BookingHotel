# VNPAY gateway debug + history consistency fix

This patch does four things:

1. Refreshes booking history from the backend whenever History opens, so Room does not show stale payment state.
2. Renames SUCCESS label to "Đã thanh toán" and PROCESSING to "Đang xử lý thanh toán".
3. Makes VNPAY config parsing robust to optional wrapping quotes and validates the 8-character TmnCode.
4. Normalizes Railway forwarded IP addresses to IPv4 for VNPAY Sandbox and logs a safe VNPAY request summary (never logs the HashSecret itself).

After deployment, create one new VNPAY payment and inspect Railway Deploy Logs for a line beginning with:

`VNPAY create bookingId=`

The line contains only non-secret request metadata plus `hashSecretLength`.
