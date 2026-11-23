package com.quachduchuy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Làm việc với cổng thanh toán VNPay (phiên bản 2.1.0).
 *
 * Luồng thanh toán:
 *  1. Website tạo URL thanh toán có chữ ký (vnp_SecureHash) rồi chuyển khách sang VNPay.
 *  2. Khách nhập thẻ test trên trang VNPay.
 *  3. VNPay chuyển khách về vnp_ReturnUrl kèm kết quả + chữ ký.
 *  4. Website kiểm tra lại chữ ký để chắc chắn dữ liệu không bị sửa, rồi cập nhật đơn.
 *
 * Chữ ký = HMAC-SHA512(hashSecret, chuỗi các tham số sắp xếp theo tên A→Z).
 */
@Service
public class VnPayService {

    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Value("${vnpay.pay-url}")
    private String payUrl;

    @Value("${vnpay.return-url}")
    private String returnUrl;

    /** Chưa điền TmnCode/HashSecret thật thì website dùng "cổng thanh toán giả lập" để demo. */
    public boolean isConfigured() {
        return !tmnCode.startsWith("YOUR_") && !hashSecret.startsWith("YOUR_");
    }

    /** Tạo các tham số gửi sang VNPay (chưa ký). */
    public Map<String, String> buildPaymentParams(String txnRef, BigDecimal amount, String orderInfo, String clientIp) {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);

        Map<String, String> params = new TreeMap<>(); // TreeMap tự sắp xếp key A→Z
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        // VNPay yêu cầu số tiền nhân 100 (bỏ phần thập phân)
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", now.format(VNP_DATE));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(VNP_DATE));
        return params;
    }

    /** URL đầy đủ để chuyển khách sang trang thanh toán VNPay. */
    public String buildPaymentUrl(Map<String, String> params) {
        return payUrl + "?" + signAndBuildQuery(params);
    }

    /** Ký các tham số và trả về chuỗi query "a=1&b=2&vnp_SecureHash=...". */
    public String signAndBuildQuery(Map<String, String> params) {
        String data = buildHashData(params);
        return data + "&vnp_SecureHash=" + hmacSHA512(hashSecret, data);
    }

    /** Kiểm tra chữ ký VNPay gửi về có hợp lệ không. */
    public boolean verifySignature(Map<String, String> allParams) {
        String receivedHash = allParams.get("vnp_SecureHash");
        if (receivedHash == null) {
            return false;
        }
        Map<String, String> fields = new TreeMap<>(allParams);
        fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        String expectedHash = hmacSHA512(hashSecret, buildHashData(fields));
        return expectedHash.equalsIgnoreCase(receivedHash);
    }

    /** Ghép các tham số (đã sắp xếp, bỏ giá trị rỗng) thành chuỗi key=value&key=value. */
    private String buildHashData(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được chữ ký VNPay", e);
        }
    }
}
