package com.example.beacon.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Service
public class TotpService {

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final String ISSUER = "Beacon";

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public String generateOtpAuthUri(String username, String secret) {
        String encodedIssuer = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedAccount = URLEncoder.encode(username, StandardCharsets.UTF_8).replace("+", "%20");
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                encodedIssuer, encodedAccount, secret, encodedIssuer
        );
    }

    /**
     * 현재 ±1 윈도우(±30초)를 허용해 기기 시간 오차를 수용한다.
     */
    public boolean verifyTotp(String base32Secret, String code) {
        if (code == null || code.length() != 6) return false;
        long counter = System.currentTimeMillis() / 1000 / 30;
        for (long c = counter - 1; c <= counter + 1; c++) {
            if (generateTotp(base32Secret, c).equals(code)) return true;
        }
        return false;
    }

    private String generateTotp(String base32Secret, long counter) {
        try {
            byte[] keyBytes = decodeBase32(base32Secret);
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset]     & 0x7F) << 24)
                       | ((hash[offset + 1] & 0xFF) << 16)
                       | ((hash[offset + 2] & 0xFF) << 8)
                       |  (hash[offset + 3] & 0xFF);

            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new RuntimeException("TOTP 생성 실패", e);
        }
    }

    private String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_CHARS.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        return sb.toString();
    }

    private byte[] decodeBase32(String base32) {
        String clean = base32.trim().toUpperCase().replace("=", "");
        byte[] result = new byte[clean.length() * 5 / 8];
        int buffer = 0, bitsLeft = 0, count = 0;
        for (char c : clean.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[count++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
