/* 
In this code the parts used from examples will have a comment mentioning which example code is used.
start of used code block //*name of example used*
end of used code block //end *name of example used*
if it's the only line used //line *name of example used*

examples used and the libraries they belong to: 
// mpu6050_unifiedsensors (https://github.com/adafruit/Adafruit_MPU6050)
// Example8_SPO2 (https://github.com/sparkfun/SparkFun_MAX3010x_Sensor_Library/blob/master/examples/Example8_SPO2/Example8_SPO2.ino)
// BLE_notify (https://github.com/espressif/arduino-esp32/tree/master/libraries/BLE/examples/Notify)

The Licenses for the examples will be in Licenses.ino as provided with the examples, if provided with the Example.
Otherwise the library license through the github links are the active license.
*/

#include <Wire.h>                                                     //Example8_SPO2
#include "MAX30105.h"
#include "spo2_algorithm.h"                                           //end Example8_SPO2                      
#include <Adafruit_MPU6050.h>                                         //line mpu6050_unifiedsensors 

#include <BLEDevice.h>                                                //BLE_notify
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define HR_UUID             "beb5483e-36e1-4688-b7f5-ea07361b26a8"    //end BLE_notify

BLEServer* pServer = NULL;

BLECharacteristic* pCharHR = NULL;

bool deviceConnected = false;
bool oldDeviceConnected = false;
uint32_t value = 0;

class MyServerCallbacks: public BLEServerCallbacks {                  //BLE_notify
  void onConnect(BLEServer* pServer){
    deviceConnected = true;
  }
  void onDisconnect(BLEServer* pServer){
    deviceConnected = false;
  }
};                                                                    //end BLE_notify

Adafruit_MPU6050 mpu;                                                 //mpu6050_unifiedsensors
Adafruit_Sensor *mpu_temp, *mpu_accel, *mpu_gyro;                     //end mpu6050_unifiedsensors
MAX30105 particleSensor;                                              //Example8_SPO2

#define MAX_BRIGHTNESS 255

#if defined(__AVR_ATmega328P__) || defined(__AVR_ATmega168__)
//Arduino Uno doesn't have enough SRAM to store 100 samples of IR led data and red led data in 32-bit format
//To solve this problem, 16-bit MSB of the sampled data will be truncated. Samples become 16-bit data.
uint16_t irBuffer[100]; //infrared LED sensor data
uint16_t redBuffer[100];  //red LED sensor data
#else
uint32_t irBuffer[100]; //infrared LED sensor data
uint32_t redBuffer[100];  //red LED sensor data
#endif

int32_t bufferLength; //data length
int32_t spo2; //SPO2 value
int8_t validSPO2; //indicator to show if the SPO2 calculation is valid
int32_t heartRate; //heart rate value
int8_t validHeartRate; //indicator to show if the heart rate calculation is valid

byte pulseLED = 11; //Must be on PWM pin
byte readLED = LED_BUILTIN; //Blinks with each data read

const byte RATE_SIZE = 4; //Increase this for more averaging
byte rates[RATE_SIZE]; //Array of heart rates
byte rateSpot = 0;
long lastBeat = 0; //Time at which the last beat occurred             //end Example8_SPO2

float beatsPerMinute;
int beatAvg;

int accelAVG;
int tempAVG;

long timer = 0;
int hrMeasure = 0; 

void setup()
{
  Serial.begin(115200);

  pinMode(pulseLED, OUTPUT);
  pinMode(readLED, OUTPUT);
  
  BLEDevice::init("HCL Bracelet");                                    //BLE_notify

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharHR = pService->createCharacteristic(
                      HR_UUID, 
                      BLECharacteristic::PROPERTY_READ |
                      BLECharacteristic::PROPERTY_WRITE |
                      BLECharacteristic::PROPERTY_NOTIFY |
                      BLECharacteristic::PROPERTY_INDICATE
                      );                                              //end BLE_notify
  
  BLE2902 *pBLE2902;
  pBLE2902 = new BLE2902();
  pBLE2902->setNotifications(true);
  pCharHR->addDescriptor(pBLE2902);
  pService->start();                                                  //BLE_notify

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinPreferred(0x0);
  BLEDevice::startAdvertising();
  Serial.println("Awaiting a client connection to notify");           //end BLE_notify

  while (!mpu.begin()) {                                              //mpu6050_unifiedsensors
    Serial.println("Failed to find MPU6050 chip");
    delay(100);
  }

  Serial.println("MPU6050 Found!");
  mpu_temp = mpu.getTemperatureSensor();
  mpu_temp->printSensorDetails();

  mpu_accel = mpu.getAccelerometerSensor();
  mpu_accel->printSensorDetails();

  mpu_gyro = mpu.getGyroSensor();
  mpu_gyro->printSensorDetails();                                     //end mpu6050_unifiedsensors 

  // Initialize sensor
  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) //Use default I2C port, 400kHz speed                                                     //Example8_SPO2
  {
    Serial.println(F("MAX30105 was not found. Please check wiring/power."));
    while (1);
  }

  byte ledBrightness = 60; //Options: 0=Off to 255=50mA
  byte sampleAverage = 4; //Options: 1, 2, 4, 8, 16, 32
  byte ledMode = 2; //Options: 1 = Red only, 2 = Red + IR, 3 = Red + IR + Green
  byte sampleRate = 100; //Options: 50, 100, 200, 400, 800, 1000, 1600, 3200
  int pulseWidth = 411; //Options: 69, 118, 215, 411
  int adcRange = 4096; //Options: 2048, 4096, 8192, 16384

  particleSensor.setup(ledBrightness, sampleAverage, ledMode, sampleRate, pulseWidth, adcRange); //Configure sensor with these settings     //end Example8_SPO2

  while (particleSensor.getIR() < 50000){
    Serial.println("waiting for first contact...");
    delay(500);
  }
  
  bufferLength = 100; //buffer length of 100 stores 4 seconds of samples running at 25sps                                                   //Example8_SPO2

  //read the first 100 samples, and determine the signal range
  for (byte i = 0 ; i < bufferLength ; i++)
  {
    while (particleSensor.available() == false) //do we have new data?
      particleSensor.check(); //Check the sensor for new data

    redBuffer[i] = particleSensor.getRed();
    irBuffer[i] = particleSensor.getIR();
    particleSensor.nextSample(); //We're finished with this sample so move to next sample
    
    Serial.print(F("red="));
    Serial.print(redBuffer[i], DEC);
    Serial.print(F(", ir="));
    Serial.println(irBuffer[i], DEC);
  }

  //calculate heart rate and SpO2 after first 100 samples (first 4 seconds of samples)
  maxim_heart_rate_and_oxygen_saturation(irBuffer, bufferLength, redBuffer, &spo2, &validSPO2, &heartRate, &validHeartRate);                //end Example8_SPO2

  timer = millis() + 1000;
}

void loop()
{ 
  if(deviceConnected){
    if (spo2 > 100 || spo2 < 0){
      spo2 = 0;
    }
    if (heartRate > 255 || heartRate < 25){
      heartRate = 0;
    }
    uint8_t cHR = heartRate/2;
    uint8_t cO2 = spo2;
    uint8_t cMT = accelAVG;
    uint8_t cTP = tempAVG;
    uint32_t total = ((cTP << 24) + (cMT << 16) + (cO2 << 8) + (cHR & 0xFF));
    Serial.print("temperature: ");
    Serial.print(tempAVG);
    Serial.print(", ");
    Serial.println(cTP);
    Serial.print("accel: ");
    Serial.print(accelAVG);
    Serial.print(", ");
    Serial.println(cMT);
    Serial.print("O2: ");
    Serial.print(spo2);
    Serial.print(", ");
    Serial.println(cO2);
    Serial.print("heart rate: ");
    Serial.print(heartRate);
    Serial.print(", ");
    Serial.println(cHR);
    pCharHR->setValue(total);
    pCharHR->notify();
  }
  if (!deviceConnected && oldDeviceConnected){                        //BLE_notify
    pServer->startAdvertising();
    Serial.println("start advertising");
    oldDeviceConnected = deviceConnected;
  }
  if (deviceConnected && !oldDeviceConnected){
    oldDeviceConnected = deviceConnected;
  }                                                                   //end BLE_notify

  
  for (byte i = 25; i < 100; i++)                                                                                              //Example8_SPO2
  {
    redBuffer[i - 25] = redBuffer[i];
    irBuffer[i - 25] = irBuffer[i];
  }
  for (byte i = 75; i < 100; i++)
  {
    while (particleSensor.available() == false) //do we have new data?
      particleSensor.check(); //Check the sensor for new data
  
    digitalWrite(readLED, !digitalRead(readLED)); //Blink onboard LED with every data read
  
    redBuffer[i] = particleSensor.getRed();
    irBuffer[i] = particleSensor.getIR();
    particleSensor.nextSample(); //We're finished with this sample so move to next sample
  }
  maxim_heart_rate_and_oxygen_saturation(irBuffer, bufferLength, redBuffer, &spo2, &validSPO2, &heartRate, &validHeartRate);  //end Example8_SPO2
  
  sensors_event_t accel;                                              //mpu6050_unifiedsensors
  sensors_event_t temp;
  mpu_temp->getEvent(&temp);
  mpu_accel->getEvent(&accel);                                        //end mpu6050_unifiedsensors

  int x = accel.acceleration.x;
  int y = accel.acceleration.y;
  int z = accel.acceleration.z;

  if (x < 0){
    x *= -1;
  }
  if (y < 0){
    y *= -1;
  }
  if (z < 0){
    z *= -1;
  }
  accelAVG = x + y + z;
  tempAVG = temp.temperature;
}
