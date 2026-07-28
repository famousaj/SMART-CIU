#include <Arduino.h>
#include "TokenDecrypter.h"
#include <LiquidCrystal_I2C.h>
#include <Keypad.h>

const byte ROWS = 4; 
const byte COLS = 3; 

char hexaKeys[ROWS][COLS] = {
  {'1', '2', '3'},
  {'4', '5', '6'},
  {'7', '8', '9'},
  {'*', '0', '#'}
};

byte rowPins[ROWS] = {13, 12, 14, 27}; 
byte colPins[COLS] = {26, 25, 33}; 

Keypad customKeypad = Keypad(makeKeymap(hexaKeys), rowPins, colPins, ROWS, COLS);
LiquidCrystal_I2C lcd(0x27, 16, 2); 
const int MAX_CHARS = 25;

String inputPassword = ""; 
String getValue = "";
String tokenUnits = "";
float threshHold = 2.0f;
bool lowToken= false;

unsigned long lastDeductionTime = 0;   
unsigned long lastLowTokenNotificationTime = 0;
const unsigned long DEDUCTION_INTERVAL = 10000; 
const unsigned long NOTIFICATION_TIMEOUT = 300000 ;

unsigned long lastInteractionTime = 0; 
const unsigned long IDLE_TIMEOUT = 8000; 

bool isTyping = false;                

static String getInfo(String shortCode) {
  return Tokens::shortCodeHandler(shortCode);
}

static String tokenHandler(String tokenNumber){
  return Tokens::tokenUpdate(tokenNumber);
}

void displayLiveTokenStatus() {
  lcd.setCursor(0, 0);
  lcd.print("Live Tokens Mon:");
  lcd.setCursor(0, 1);
  lcd.print("Units: ");
  lcd.print(Tokens::tokens); 
  lcd.print(" pts      "); 
}

void displayLiveTyping() {
  lcd.clear();
  int len = inputPassword.length();
  if (len <= 16) {
    lcd.setCursor(0, 0);
    lcd.print(inputPassword);
  } else {
    lcd.setCursor(0, 0);
    lcd.print(inputPassword.substring(0, 16));
    lcd.setCursor(0, 1);
    lcd.print(inputPassword.substring(16, len));
  }
}


void displayProcessedResult(String resultText) {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Processing...");
  delay(1200);
  lcd.clear();

  int decLen = resultText.length();
  lcd.setCursor(0, 0);
  if (decLen <= 16) {
    lcd.print(resultText);
  } else {
    lcd.print(resultText.substring(0, 16));
    lcd.setCursor(0, 1);
    lcd.print(resultText.substring(16, min(decLen, 32)));
  }
}

static void keyPadHandler(){
  char key = customKeypad.getKey();
  if (key) {
    isTyping = true; 
    lastInteractionTime = millis(); 

    if (key == '#') {
      if (inputPassword.length() > 0) {
        String result;
        if (inputPassword.length() <= 16) {
          result = getInfo(inputPassword);
        } else {
          result = tokenHandler(inputPassword);
        }
        Serial2.println(result);
        Serial.println("Processed Result: " + result);
        displayProcessedResult(result); 
        delay(4000); 
        inputPassword = "";
        isTyping = false;
        lcd.clear();
      }
    } 
    
    else if (key == '*') {
      int len = inputPassword.length();
      if (len > 0) {
        
        inputPassword = inputPassword.substring(0, len - 1); 
        
        if (inputPassword.length() == 0) {
          
          isTyping = false;
          lcd.clear();
        } else {
          
          displayLiveTyping(); 
        }
      }
    } 
    else {
      if (inputPassword.length() < MAX_CHARS) {
        inputPassword += key;
        displayLiveTyping(); 
      } else {
        Serial.println("Warning: Maximum 25 character limit reached!");
      }
    }
  }
}

static String oneTimeCheck(){
  if(Tokens::tokens <= threshHold){
    return String(Tokens::tokens);
  }
  return "";
}

static void tokenChecker(void * parameter) {
  for(;;) { 
    if (Tokens::tokens <= threshHold) {
      lowToken = true;
    } else {
      lowToken = false;
    }
    vTaskDelay(pdMS_TO_TICKS(1000));
  }
}


void setup() {
  Serial.begin(115200);
  Serial2.begin(115200, SERIAL_8N1, 16, 17); 
  Serial.println("UART Systems Online.");    

  
  lcd.init();         
  lcd.backlight();    
  
  lcd.setCursor(0, 0);
  lcd.print("Token Simulator");
  lcd.setCursor(0, 1);
  lcd.print("Keypad Initialed");
  delay(2000);
  lcd.clear();
  
  lastDeductionTime = millis();
  lastLowTokenNotificationTime = millis() - NOTIFICATION_TIMEOUT;

  xTaskCreatePinnedToCore(tokenChecker, "TokenTask", 2048, NULL, 0, NULL, 1 );

}

void loop() {

  unsigned long currentMillis = millis();

if (lowToken) {
    if (currentMillis - lastLowTokenNotificationTime >= NOTIFICATION_TIMEOUT) {
      Serial2.println("Low Tokens: " + String(Tokens::tokens));
      Serial.println("System Event -> Dispatched Low Token Alert via UART: " + String(Tokens::tokens));
      lastLowTokenNotificationTime = currentMillis; 
    }
  }


  keyPadHandler();


  if (!isTyping) {
    displayLiveTokenStatus();

    if (millis() - lastDeductionTime >= DEDUCTION_INTERVAL) {
      if (Tokens::tokens >= 0.0) {
        Tokens::tokens -= 0.1; 
        Serial.print("Simulated Usage Deduction! Current Balance: ");
        Serial.println(Tokens::tokens);
      }
      lastDeductionTime = millis(); 
    }
  } 
  else {
    
    if (millis() - lastInteractionTime >= IDLE_TIMEOUT) {
      inputPassword = "";
      isTyping = false;
      lcd.clear();
      Serial.println("Typing session timed out due to user inactivity.");
    }
  }

 
  if (Serial2.available() > 0) {
    String incomingData = Serial2.readStringUntil('\n');
    incomingData.trim();

    if (incomingData.length() > 0) {
      if (!isTyping) { 
        isTyping = true; 
        inputPassword = incomingData;

        if (incomingData == "GET_METER_NO") {
            
            Serial2.println("METER: " + String(Tokens::meterNumber)); 
            Serial.println("METER: " + String(Tokens::meterNumber));
          }

        else if(incomingData == "CHECK_TOKENS"){
          String Token = oneTimeCheck();
          if(Token != ""){
          Serial2.println("TOKENS " + Token);
          Serial.println("TOKENS: " + Token);}
        }

        else if(incomingData.startsWith("THRESHOLD")){
          
          int colonIndex = incomingData.indexOf(":");

          if(colonIndex != -1){
            String dataString = incomingData.substring(colonIndex +2);
            threshHold = dataString.toFloat();
            Serial.println("THRESHOLD SET TO: " + String(threshHold));
            displayProcessedResult("THRESHOLD SET TO: " + String(threshHold));
            delay(4000);
          }

        }

        else if(incomingData.startsWith("PIN")){
          displayProcessedResult(incomingData);
          delay(4000);
        }

        else if(incomingData == "BONDS_CLEARED"){
          displayProcessedResult(incomingData);
          delay(4000);
        }

        else if (incomingData.length() == 3) {
          getValue = getInfo(incomingData);
          Serial2.println(getValue);
          displayProcessedResult(getValue); 
        } else if (incomingData.length() == 20) {
          tokenUnits = tokenHandler(incomingData);
          Serial.println("Received From Spiderman: " + incomingData + " | " + incomingData); 
          String totalUnits = String(Tokens::tokens);
          
          Serial2.println(tokenUnits);
          Serial2.println(String("Total Units: ") + totalUnits);
          displayProcessedResult(tokenUnits); 
          Serial.println("Sent to Spiderman: " + tokenUnits + " | " + totalUnits); 
        }
        else{
          Serial2.println("Invalid Input.");
          Serial.println(incomingData);
          Serial.println("Invalid Input.");
          displayProcessedResult("Invalid Input.");
          delay(4000);
        }
        
        delay(4000); 
        inputPassword = "";
        isTyping = false; 
        lcd.clear();
      } else {
        Serial2.println("BUSY: User is currently using Keypad.");
      }
    }
  }
}