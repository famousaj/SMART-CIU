package Redknight.thread.BLE.tokens;

import  Redknight.thread.BLE.tokens.TokenGenerator;
import  Redknight.thread.BLE.ble.BleConstants;

public class Tokens {

    static long meterNumber = Long.parseLong(BleConstants.METER_NUMBER);
    static double tokens ;
    static double rCharges = 25.2;
    static double mCharges = 0;

    public static String awardToken(int amount) throws Exception {
        if(amount <= 100 ){
            mCharges = 0;
            tokens= (amount - mCharges)/rCharges;
            return   TokenGenerator.generateToken(meterNumber, (int) tokens);
        }

        else if(amount <= 500){
            mCharges = 7;

            double updtTokens = (amount - mCharges)/rCharges;
            tokens = tokens + updtTokens;
            return TokenGenerator.generateToken(meterNumber, (int) tokens);
        }

        else if(amount <= 1000){
            mCharges = 13;

            double updtTokens = (amount - mCharges)/rCharges;
            tokens = tokens + updtTokens;
            return TokenGenerator.generateToken(meterNumber, (int) tokens);
        }

        else if(amount <= 1500){
            mCharges = 23;

            double updtTokens= (amount - mCharges)/rCharges;
            tokens = tokens + updtTokens;
            return TokenGenerator.generateToken(meterNumber, (int) tokens);
        }

        else if(amount <= 2000){
            mCharges = 33;

            double updtTokens = (amount - mCharges)/rCharges;
            tokens = tokens + updtTokens;
            return TokenGenerator.generateToken(meterNumber, (int) tokens);
        }

        else if(amount <= 5000){
            mCharges = 53;

            double updtTokens = (amount - mCharges)/rCharges;
            tokens = tokens + updtTokens;
            return TokenGenerator.generateToken(meterNumber, (int) tokens);
        }

        else if(amount <= 10000){
            mCharges = 78;

            double updtTokens = (amount - mCharges)/rCharges;
            tokens = tokens + updtTokens;
            return TokenGenerator.generateToken(meterNumber, (int) tokens);
        }

        else {
            mCharges = 150;

            double updtTokens = (amount - mCharges)/rCharges;
            tokens = tokens + updtTokens;
            return TokenGenerator.generateToken(meterNumber, (int) tokens);
        }

    }
}
