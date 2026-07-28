#ifndef TOKEN_DECRYPTER_H
#define TOKEN_DECRYPTER_H

#include <Arduino.h>
#include <iostream>
#include <random>
#include <string>

class TokenDecrypter {
public:
    static bool decryptToken(const String& tokenCode, uint64_t& outMeter, uint32_t& outTokens) {
        // 1. Validation: Ensure the token is exactly 20 digits
        if (tokenCode.length() != 20) {
            Serial.println("Error: Token must be exactly 20 digits!");
            return false;
        }

        // Ensure all characters are numeric digits
        for (int i = 0; i < 20; i++) {
            if (!isDigit(tokenCode.charAt(i))) {
                Serial.println("Error: Token contains non-numeric characters!");
                return false;
            }
        }

        // 2. Slice the 20-digit token (Kenyan Standard: 11-digit Meter ID)
        String meterPart = tokenCode.substring(0, 11);     // Digits 0 to 10 (11 chars)
        String unitsPart = tokenCode.substring(11, 16);    // Digits 11 to 15 (5 chars)
        String checksumPart = tokenCode.substring(16, 20); // Digits 16 to 19 (4 chars)

        // 3. Verify Integrity Checksum
        String basePayload = meterPart + unitsPart;
        long sum = 0;
        for (int i = 0; i < basePayload.length(); i++) {
            sum += (basePayload.charAt(i) - '0') * (i + 1);
        }
        int calculatedSum = sum % 10000;
        int providedSum = checksumPart.toInt();

        if (calculatedSum != providedSum) {
            Serial.println("Security Check Failed: Token checksum validation failed!");
            return false;
        }

        // 4. Output values
        // Parse the 11-digit Meter string into a uint64_t variable
        outMeter = 0;
        for (int i = 0; i < meterPart.length(); i++) {
            outMeter = (outMeter * 10) + (meterPart.charAt(i) - '0');
        }

        // Parse 5-digit units string to integer units.
        // Since the generator scales units by 10 to keep decimals (e.g., 5.0 -> 00050),
        // we divide by 10 to assign the actual whole token integer value.
        outTokens = unitsPart.toInt() / 10; 

        return true;
    }
};

class Tokens {
private:
    inline static std::random_device rnd;
    inline static std::mt19937 gen {rnd()};
    inline static std::uniform_real_distribution<float> distrib{20.0f, 50.0f};
    inline static std::uniform_real_distribution<float> dailyRd{0.2f, 0.9f};
    inline static std::uniform_real_distribution<float> tokenRd{1.2f, 10.9f};
    inline static std::uniform_int_distribution<int> voltageRd{240, 250};
    inline static std::uniform_real_distribution<float> amperedRd{10.0, 13.9};

public:
    inline static const String meterNumber = "48720993827";
    inline static float tokens = 1.8f;
    inline static float dailyUnits = 0.0f;
    inline static float monthUnits = 0.0f;
    inline static int voltage = 0;
    inline static float amperes = 0.0f;
    inline static float rCharges = 25.2f;
    inline static int mCharges;

    Tokens() {} // Fixed empty constructor definition

    static String shortCodeHandler(String shortCode) {
        if(shortCode == "000") {
            return "MNo: " + meterNumber;
        }
        else if(shortCode == "049") {
            return "Monthly Units: "+ String(monthUnits = distrib(gen));
        }
        else if(shortCode == "004") {
            return String(voltage = voltageRd(gen)) + "Volts";
        }
        else if(shortCode == "005") {
            return String(amperes = amperedRd(gen)) + "Amps";
        }
        else if(shortCode == "001") {
            return "Tokens: " + String(tokens);
        }
        else if(shortCode == "002") {
            return "Daily Units: " + String(dailyUnits = dailyRd(gen));
        }
        else {
            return "Short Code not Applicable";
        }
    }

    // Now accepts the Base64 token string sent via simulation/keypad
    static String tokenUpdate(String tokenCode) {
        uint64_t decryptedMeter = 0;
        uint32_t decryptedAmount = 0;

        // Convert std::string to Arduino String structure for the decrypter tool
        String tokenStr = String(tokenCode.c_str());

        // Perform cryptography and security loop
        if (TokenDecrypter::decryptToken(tokenStr, decryptedMeter, decryptedAmount)) {
            
            // Check if the decrypted token was built for this specific hardware target
            String decryptedMeterStr = String(decryptedMeter);
            
            if (decryptedMeterStr == meterNumber) {
                tokens = tokens + (float)decryptedAmount;
                return "Token Value: " + String(decryptedAmount) + " | " + tokenCode;
            } else {
                return "Error: Token belongs to a different Meter ID!";
            }
        } else {
            return "Error: Decryption or Authenticity validation failed.";
        }
    }
};

#endif