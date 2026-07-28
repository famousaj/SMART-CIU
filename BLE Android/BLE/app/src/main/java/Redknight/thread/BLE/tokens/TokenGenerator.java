package Redknight.thread.BLE.tokens;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenGenerator {
    public static String generateToken(long meterId, double units) {
        // 1. Format Meter ID to exactly 11 digits (Kenyan standard)
        String meterStr = String.format("%011d", meterId);

        // 2. Scale units by 10 to preserve decimal and pad to 5 digits (e.g., 5.0 -> 00050)
        long scaledUnits = Math.round(units * 10);
        String unitsStr = String.format("%05d", scaledUnits);

        // Combine into 16-digit base payload
        String basePayload = meterStr + unitsStr;

        // 3. Generate a 4-digit checksum
        int checksum = calculateChecksum(basePayload);
        String checksumStr = String.format("%04d", checksum);

        // Return final 20-digit token
        return basePayload + checksumStr;
    }

    private static int calculateChecksum(String data) {
        long sum = 0;
        for (int i = 0; i < data.length(); i++) {
            sum += (data.charAt(i) - '0') * (i + 1);
        }
        return (int) (sum % 10000);
    }

    }


