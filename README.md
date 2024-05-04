# README:

## ARMBAND

De armband meet hartslag, lichaamstemperatuur, zuurstofgehalte en beweging van de persoon die
de armband draagt. Voor die metingen zit voor ieder een sensor ingebouwd.

### Componenten

MPU-6050 module (accelerometer)
max30102 module (hartslagsensor, zuurstofgehaltemeter, lichaamstemperatuursensor)
SEEED XIAO ESP32S3 (microcontroller)

### Setup

Zorg ervoor dat alle componenten aangesloten zijn. Pins kunnen gewijzigd worden.
Sluit de microcontroller aan met de computer en flash the software naar de microcontroller.
Nu heb je de software op de armband en hoeft niks aangepast te worden.

## APP

De app ontvangt de metingen van de armband over Bluetooth low energy. Daarna slaat de app de gegevens op
in een tekst bestand.

### Android Studio gebruiken

Download de bestanden van deze github.

In Android Studio selecteer open.

![project manager](https://github.com/AmatMN/Diabeeeteeees/blob/main/project%20manager.png)

Selecteer de BraceletApp map van de gedownloade github bestanden.

![map select](https://github.com/AmatMN/Diabeeeteeees/blob/main/map%20select.png)

Android Studio laad nu het project in. Dit kan even duren voordat alles helemaal geladen is.

De belangrijkste bestanden staan hier:

```
App --- java --- com.example.braceletapp --- MainActivity (het hoofd programma van de app. Meeste van de code staat hier.)
     |                                    |
     |                                    -- FirstFragment (de code voor het eerste scherm waar de inkomende metingen worden weergeven.)
     |                                    |
     |                                    -- SecondFragment (de code voor het vebindings scherm.)
     |                                    |
     |                                    -- ThirdFragment (de code voor het opties scherm.)
     |          
     -- res --- layout --- activity_main.xml (de layout van de app)
             |          |
             |          -- fragment_first.xml (de layout van het eerste scherm. Metingen)
             |          |
             |          -- fragment_second.xml (de layout van het tweede scherm. Verbingingen)
             |          |
             |          -- fragment_third.xml (de layout van het derde scherm. Opties)
             |
             -- menu --- bottom_nav_menu.xml (de layout van de knoppen onderaan het scherm.)
             |
             -- values --- colors.xml (opslag van kleurwaardes.)             
                        |
                        -- strings.xml (opslag van tekst die niet tijdens run-time wordt gegenereerd.)
```
![handleiding](https://github.com/AmatMN/Diabeeeteeees/assets/55703008/46439d3c-c19c-4d96-88d7-a88b625fc399)

## BRONNEN

Hier staan de links naar de bronnnen gebruikt in de code. De links staan ook in de commentaarblokken in de code zelf.

### App

https://stackoverflow.com/questions/4597690/how-to-set-timer-in-android (android timer voor het updaten van de fragments)
https://www.youtube.com/watch?v=exsvuXbk_2U (android BLE notify en permissions)

### Bracelet

https://github.com/adafruit/Adafruit_MPU6050
https://github.com/sparkfun/SparkFun_MAX3010x_Sensor_Library/blob/master/examples/Example8_SPO2/Example8_SPO2.ino
https://github.com/espressif/arduino-esp32/tree/master/libraries/BLE/examples/Notify
