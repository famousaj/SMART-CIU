package Redknight.thread.BLE.items;

import Redknight.thread.BLE.ble.BleConstants;

public class Tokens {

    private final String originalToken;
    private String displayToken;
    private final String meterNumber;
    private boolean isUsed;


    public Tokens(String displayToken, String originalToken, String meterNumber, boolean isUsed) {
        this.displayToken = displayToken;
        this.originalToken = originalToken;
        this.meterNumber = BleConstants.METER_NUMBER;
        this.isUsed = isUsed;
    }

    public String getOriginalToken() {
        return originalToken;
    }

    public String getDisplayToken() {
        return displayToken;
    }

    public String getMeterNumber() {
        return meterNumber;
    }

    public boolean isUsed() {
        return isUsed;
    }

    private String formatToken(String token) {
        if (token.length() != 20) return token;
        return token.substring(0, 5) + " - " +
                token.substring(5, 10) + " - " +
                token.substring(10, 15) + " - " +
                token.substring(15, 20);
    }

    public void markAsUsedAndScramble() {
        this.isUsed = true;
        // Scramble logic: replaces all numeric digits with characters or asterisks
        StringBuilder scrambled = new StringBuilder();
        for (int i = 0; i < originalToken.length(); i++) {
            char c = originalToken.charAt(i);
            if (Character.isDigit(c)) {
                scrambled.append("X"); // Or replace randomly: (char)('A' + new java.util.Random().nextInt(26))
            } else {
                scrambled.append(c);
            }
        }
        this.displayToken = scrambled.toString();
    }
}
