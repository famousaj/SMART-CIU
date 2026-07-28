#include <BLE2902.h>
#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEAdvertising.h>
#include <BLECharacteristic.h>
#include <BLEService.h>
#include <BLEDescriptor.h>
#include <stdio.h>
#include <string.h>
#include <esp32-hal-gpio.h>
#include <HardwareSerial.h>
#include <BLE2901.h>
#include <Wire.h>
#include<mbedtls/md.h>




#define SERVICE_UUID "b2c5bee9-8200-450b-88cc-5fae669e7176"
#define CHARACTERISTIC_UUID "488c41d1-51f9-4f31-b27e-0030dc0824da"
#define lRed 25
#define lGreen 26
#define lYellow 32
#define btnReset 33

BLECharacteristic *pCharacteristic;
String dynamicDeviceName = ""; 
String lastKnownTokenResult = "No Data Yet";
String offlineMessageBuffer = ""; 
const char* SHARED_SECRET = "3aeac170-5890-472e-b284-249541588fea"; 
bool isAppVerified = false;
bool ACK = false;
unsigned long espTimeOffset = 0;   
unsigned long syncMillis = 0; 
bool messageBuffer= false;
bool isConnected = false;
bool isBonded = false;
bool checkedTokens = false;
volatile bool eventFlag = false;
volatile uint32_t dynamicPIN = 0;
int BOND_LIMIT = 1;
int long blinkingInterval = 1000;

uint32_t getTargetTOTP() {
    // 1. Calculate the current runtime Unix epoch timestamp
    unsigned long currentUnixTime = espTimeOffset + ((millis() - syncMillis) / 1000);
    
    // 2. Determine the 30-second time step window
    uint64_t timeStep = currentUnixTime / 30; 

    // 3. Prepare the time block array for hashing (Big-Endian format)
    uint8_t msg[8];
    for (int i = 7; i >= 0; i--) {
        msg[i] = timeStep & 0xFF;
        timeStep >>= 8;
    }

    // 4. Execute the HMAC-SHA1 algorithm using mbedtls
    uint8_t hmacResult[20];
    mbedtls_md_context_t ctx;
    mbedtls_md_init(&ctx);
    mbedtls_md_setup(&ctx, mbedtls_md_info_from_type(MBEDTLS_MD_SHA1), 1);
    mbedtls_md_hmac_starts(&ctx, (const unsigned char*)SHARED_SECRET, strlen(SHARED_SECRET));
    mbedtls_md_hmac_update(&ctx, msg, 8);
    mbedtls_md_hmac_finish(&ctx, hmacResult);
    mbedtls_md_free(&ctx);

    // 5. Dynamic truncation to extract a 4-byte integer from the 20-byte hash
    int offset = hmacResult[19] & 0x0F;
    uint32_t binCode = (hmacResult[offset] & 0x7F) << 24 |
                       (hmacResult[offset + 1] & 0xFF) << 16 |
                       (hmacResult[offset + 2] & 0xFF) << 8  |
                       (hmacResult[offset + 3] & 0xFF);

    // 6. Restrict to a standard 6-digit numeric pin
    return binCode % 1000000;
}



class MyServercb : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) {
    isConnected = true;
    checkedTokens = false;
    digitalWrite(lRed, HIGH);
    pCharacteristic->setValue("Connected");
    pCharacteristic->notify();
    Serial.println("Client Joined The Meeting");
  }

  void onDisconnect(BLEServer *pServer) {
    isConnected = false;
    isBonded = false;
    checkedTokens = false;
    isAppVerified = false;
    digitalWrite(lRed, LOW);
    digitalWrite(lGreen, LOW);
    digitalWrite(lYellow, LOW);
    Serial.println("Client Left");
    BLEDevice::startAdvertising();
  }
};

class MyCTCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    String Message = pCharacteristic->getValue().c_str();
    Message.trim();

    Serial.println("Message Received via BLE: " + Message);

    if (Message.startsWith("SYNC_AUTH:")) {
        int firstColon = Message.indexOf(':', 10);
        String timeStr = Message.substring(10, firstColon);
        String pinStr = Message.substring(firstColon + 1);

        // Synchronize the internal reference clock
        espTimeOffset = strtoul(timeStr.c_str(), NULL, 10);
        syncMillis = millis();

        // Generate target verification token for this exact time step
        uint32_t expectedPin = getTargetTOTP();
        uint32_t receivedPin = pinStr.toInt();

        // Validate the PIN
        if (receivedPin == expectedPin) {
            isAppVerified = true;
            Serial.println("TOTP VALIDATED: Absolute security cleared. Access granted!");
            digitalWrite(lGreen, HIGH);
            ACK = true;
            return;
        } else {
            Serial.printf("SECURITY BREACH: Wrong TOTP pin! Expected %06d, got %06d. Disconnecting...\n", expectedPin, receivedPin);
            esp_ble_gatts_close(0, 0);
            isAppVerified = false;
            ACK = true;
            return;
        }
    }

    if (Message.length() > 0 && Message != "SYNC_AUTH" && isAppVerified) {
      if (Message == "ON" || Message == "on") {
        digitalWrite(lYellow, HIGH);
        digitalWrite(lGreen, HIGH);
      } else if (Message == "OFF" || Message == "off") {
        digitalWrite(lYellow, LOW);
        digitalWrite(lGreen, LOW);
      } else if (Message == "Green" || Message == "green") {
        digitalWrite(lGreen, HIGH);
        digitalWrite(lYellow, LOW);
      } else if (Message == "Yellow" || Message == "yellow") {
        digitalWrite(lGreen, LOW);
        digitalWrite(lYellow, HIGH);
      } else {
        Serial2.println(Message);
        Serial.println("Forwarded to Keypad via UART: " + Message);
      }
    }
  }

  void onRead(BLECharacteristic *pCharacteristic) {
    pCharacteristic->setValue((uint8_t*)lastKnownTokenResult.c_str(), lastKnownTokenResult.length());
  }
};

class MySecurityCallbacks : public BLESecurityCallbacks {
  void onPassKeyNotify(uint32_t pass_key) override {
    dynamicPIN = pass_key;
    Serial.print("Passkey Notify: ");
    Serial.println(pass_key);  
    Serial2.println("PIN:" + String(pass_key));
    delay(300);
    eventFlag = true;
  }

  bool onSecurityRequest() override {
    Serial.println("Client Requested Secure Connection...");

    int currentBondedDevices = esp_ble_get_bond_device_num();

    if(currentBondedDevices == BOND_LIMIT){
      Serial.println("Connection rejected: Bond limit reached.");
      return false;
    }
      Serial.println("Connection Allowed");
    return true;

  }

  void onAuthenticationComplete(esp_ble_auth_cmpl_t auth_cmpl) override {
    if (auth_cmpl.success) {
      Serial.println("Bonding Successful, Keys stored!");
      isBonded = true;
    } else {
      Serial.printf("Bonding failed. Reason : %d\n", auth_cmpl.fail_reason);
      isBonded = false;
    }
  }
};


void initializeBLE(String deviceName) {
  char nameBuf[32];
  deviceName.toCharArray(nameBuf, 32);

  BLEDevice::init(nameBuf);
  BLEDevice::setSecurityCallbacks(new MySecurityCallbacks());

  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServercb());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
      CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_READ | 
      BLECharacteristic::PROPERTY_WRITE | 
      BLECharacteristic::PROPERTY_NOTIFY | 
      BLECharacteristic::PROPERTY_READ_ENC | 
      BLECharacteristic::PROPERTY_WRITE_ENC
  );
  pCharacteristic->setAccessPermissions(ESP_GATT_PERM_READ_ENC_MITM | ESP_GATT_PERM_WRITE_ENC_MITM);
  pCharacteristic->setCallbacks(new MyCTCallbacks());

  BLE2901 *pdetDescriptor = new BLE2901();
  BLEDescriptor *pNotDescriptor = new BLEDescriptor(BLEUUID((uint16_t)0x2902));
  
pCharacteristic->addDescriptor(new BLE2902()); 
pdetDescriptor->setValue("Avenger");
pCharacteristic->addDescriptor(pdetDescriptor);

  pService->start();

  BLESecurity *pSecurity = new BLESecurity();
  pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_MITM_BOND);
  pSecurity->setCapability(ESP_IO_CAP_OUT);
  pSecurity->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
  pSecurity->setRespEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);

  BLEAdvertising *pAds = BLEDevice::getAdvertising();

  

  BLEAdvertisementData adsData;
  adsData.setFlags(ESP_BLE_ADV_FLAG_GEN_DISC | ESP_BLE_ADV_FLAG_BREDR_NOT_SPT);
  adsData.setName(deviceName); 

  
  BLEAdvertisementData scanData;
  scanData.setCompleteServices(BLEUUID(SERVICE_UUID));
  scanData.setName(deviceName); 

  pAds->setAdvertisementData(adsData);
  pAds->setScanResponseData(scanData);

  pAds->setScanResponse(true);
  pAds->setMinPreferred(0x06); 
  pAds->setMinPreferred(0x12);

  BLEDevice::startAdvertising();
  Serial.println("BLE Stack started with Device Name: " + deviceName);
}

void clearAllStoredBonds() {
  int dev_num = esp_ble_get_bond_device_num();
  if (dev_num > 0) {
    esp_ble_bond_dev_t *dev_list = (esp_ble_bond_dev_t *)malloc(sizeof(esp_ble_bond_dev_t) * dev_num);
    esp_ble_get_bond_device_list(&dev_num, dev_list);
    
    for (int i = 0; i < dev_num; i++) {
      esp_ble_remove_bond_device(dev_list[i].bd_addr); 
    }
    free(dev_list);
    Serial.println("All old bonds wiped successfully.");
    Serial2.println("BONDS_CLEARED");
  }
}
void setup() {
  Wire.begin(21, 22); 

  pinMode(btnReset, INPUT_PULLUP);
  pinMode(lRed, OUTPUT);
  pinMode(lGreen, OUTPUT);
  pinMode(lYellow, OUTPUT);
  Serial.begin(115200);
  Serial2.begin(115200, SERIAL_8N1, 16, 17);
  
  Serial.println("System Initialized. Awaiting Meter Number...");


  while (dynamicDeviceName == "") {
    
    Serial2.println("GET_METER_NO"); 
    
    unsigned long startWait = millis();
    while (millis() - startWait < 2000) { 
      if (Serial2.available() > 0) {
        String input = Serial2.readStringUntil('\n');
        input.trim();
        if (input.length() > 0 && input.startsWith("METER:")) {
          dynamicDeviceName = input.substring(6); 
          Serial.println("Received Meter Number: " + dynamicDeviceName);
          break;
          
        }
      }
    }
    if(dynamicDeviceName == "") {
      Serial.println("Retrying connection to Keypad for Meter ID...");
    }
  }

  delay(1000);

  
  initializeBLE(dynamicDeviceName);
}

unsigned long lastNotifyTime = 0;
const long interval = 10000;

unsigned long lastBlinkTime = 0;

void loop() {
  unsigned long currentMillis = millis();

  if (currentMillis - lastBlinkTime >= blinkingInterval && !isConnected) {
    lastBlinkTime = currentMillis;

    digitalWrite(lRed, !digitalRead(lRed));
  }

  if(digitalRead(btnReset) == LOW){
    Serial.println("Button pressed! Wiping bond memory...");
    clearAllStoredBonds();
    messageBuffer = false;
    offlineMessageBuffer = "";
    if(isConnected){
      esp_ble_gatts_close(0,0);
      Serial.println("Disconnecting Device...");
    }
    delay(500);
    BLEDevice::startAdvertising();
    Serial.println("Ready To Connect");
  }

  if(isConnected && isBonded && !checkedTokens){
    Serial2.println("CHECK_TOKENS");
    Serial.println("Checking to see if the Threshold is Passed");
    checkedTokens = true;
  }

  if(ACK && isConnected && isBonded && !isAppVerified){
    Serial.println("Client Connected but not Authenticated. Awaiting TOTP Validation...");
    pCharacteristic->setValue("App Not Verified");
    pCharacteristic->notify();
    ACK = false;
  }else if(ACK && isConnected && isBonded && isAppVerified){
    pCharacteristic->setValue("App Verified");
    pCharacteristic->notify();
    Serial.println("Client Authenticated Successfully. Access Granted!");
    ACK = false;
  }

  if(isConnected && isBonded && messageBuffer && offlineMessageBuffer != "" && isAppVerified){
      Serial.println("User Offline Message Found in Buffer: " + offlineMessageBuffer);
      Serial.println("Dispatching to Android via BLE Notification stream...");
      pCharacteristic ->setValue(offlineMessageBuffer.c_str());
      pCharacteristic -> notify();
      messageBuffer = false;
      offlineMessageBuffer = "";

  }

if (Serial2.available() > 0) {
  String incomingUartData = Serial2.readStringUntil('\n');
  
  incomingUartData.replace("\r", "");
  incomingUartData.replace("\n", "");
  incomingUartData.trim(); 


  if (incomingUartData.length() > 0) {
    Serial.println("Received from Keypad over UART: " + incomingUartData);
    
    if (isConnected && isBonded && isAppVerified && !incomingUartData.startsWith("METER")) {
      // 1. Store and set the value cleanly using byte arrays
      int stringLength = incomingUartData.length();
      lastKnownTokenResult = incomingUartData;
      pCharacteristic->setValue(incomingUartData.c_str());
      
      // 2. Automatically push everything to Android instantly
      pCharacteristic->notify();
      Serial.println("Sent: " + incomingUartData);
      Serial.println("-> Dispatched data via BLE Notification stream.");
    }else if (!isConnected && incomingUartData.startsWith("Token Value")) {
      messageBuffer = true;
      offlineMessageBuffer = incomingUartData;
      Serial.println("User Offline Message Saved in Buffer");
    }
    else{
      Serial.println("-> Not sent via BLE Notification stream. Either not connected or not bonded.");
    }
  }
} delay(10);
}