package Redknight.thread.BLE.Cage;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class redCage {
    private static final String SHARED_SECRET = "3aeac170-5890-472e-b284-249541588fea";

    public static String generatePayload() {
        // 1. Get exact current Unix epoch time in seconds
        long unixTimeSeconds = System.currentTimeMillis() / 1000;
        long timeStep = unixTimeSeconds / 30;

        // 2. Encode the time block value to bytes
        byte[] msg = new byte[8];
        for (int i = 7; i >= 0; i--) {
            msg[i] = (byte) (timeStep & 0xFF);
            timeStep >>= 8;
        }

        try {
            // 3. Compute HMAC-SHA1
            SecretKeySpec signingKey = new SecretKeySpec(SHARED_SECRET.getBytes(), "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            byte[] hmacResult = mac.doFinal(msg);

            // 4. Dynamic Truncation
            int offset = hmacResult[hmacResult.length - 1] & 0xF;
            long binCode = ((hmacResult[offset] & 0x7F) << 24)
                    | ((hmacResult[offset + 1] & 0xFF) << 16)
                    | ((hmacResult[offset + 2] & 0xFF) << 8)
                    | (hmacResult[offset + 3] & 0xFF);

            long pin = binCode % 1000000;

            // Format as padded 6 digit string
            String pinStr = String.format("%06d", pin);

            // Return the combined timestamp and code token payload
            return "SYNC_AUTH:" + unixTimeSeconds + ":" + pinStr;

        } catch (GeneralSecurityException e) {
            return null;
        }
    }
}

