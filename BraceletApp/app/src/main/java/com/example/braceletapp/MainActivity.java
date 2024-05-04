package com.example.braceletapp;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;


/*
 This project uses the GATT (Generic ATTribute) server protocol for data transfer
 The GATT server protocol works with services and characteristics
 The characteristics work on the Attribute protocol which saves data in a lookup table with 16 bit ID's
 A service is a collection of one or more characteristics in a logical collection
 Characteristics are a datapoint that is sent or received.
 A characteristic can also hold properties that tell if it sends, receives, or notifies data

 */
@RequiresApi(api = Build.VERSION_CODES.S)
public class MainActivity extends AppCompatActivity implements BottomNavigationView
        .OnItemSelectedListener {
    // this activity
    private static final String TAG = "MainActivity";
    // bottom bar for fragment selection
    BottomNavigationView bottomNavigationView;
    // a list of all addresses captured by the scanner so no duplicates are shown on the connect fragment
    private List<String> capturedAddresses;
    BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothGatt mBluetoothGatt;
    // whether or not the mBluetoothLeScanner is scanning or not
    private boolean scanningEnd;
    // a list of required permissions
    String[] permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
    // the return code for those permissions
    int requestCodePermission;
    private static final UUID NOTIFY_SERVICE = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID NOTIFY_CHAR = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    BluetoothGattCharacteristic characteristicNotify;

    FirstFragment firstFragment = new FirstFragment();
    SecondFragment secondFragment = new SecondFragment();
    ThirdFragment thirdFragment = new ThirdFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // set up the bottom navigation menu with a listener and set it to the data (first) fragment
        bottomNavigationView = findViewById(R.id.bottomnav);
        // set the listener for presses on the bottom bar
        bottomNavigationView.setOnItemSelectedListener(this);
        // set the active fragment to the data fragment
        bottomNavigationView.setSelectedItemId(R.id.data);

        // try to open the output file and write a break line in there
        try {
            commitToFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // try to open the output file and read the file to show in the log of the first fragment
        String temp = "";
        try {
            temp = readFromFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // try to start the first fragment with the existing log
        firstFragment.Startup(temp);

        capturedAddresses = new ArrayList<>();

        // set up a timer to call update on the first and second fragments every 500 milliseconds
        // https://stackoverflow.com/questions/4597690/how-to-set-timer-in-android
        // parts of multiple answers were used of the above link to make the timer
        Timer timer2 = new Timer();
        timer2.scheduleAtFixedRate(new TimerTask()
        {
            public void run()
            {
                firstFragment.update(scanningEnd);
                secondFragment.update(scanningEnd);
            }
        },0,500);

        // request permissions if missing else start scanning for bluetooth low energy devices
        // https://www.youtube.com/watch?v=exsvuXbk_2U
        if (!hasPermissions(MainActivity.this, permissions)) {
            requestPermissions(permissions, requestCodePermission);
        } else {
            scanLeDevice();
        }
    }

    /*
    This function is called whenever the user clicks on the bottom navigation menu
    and sets it to the correct fragment
    */
    public boolean onNavigationItemSelected(@NonNull MenuItem item)
    {
        if (item.getItemId() == R.id.data){
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, firstFragment)
                    .commit();
            return true;
        }else if (item.getItemId() == R.id.connect){
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, secondFragment)
                    .commit();
            return true;
        }else if (item.getItemId() == R.id.settings){
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, thirdFragment)
                    .commit();
            return true;
        }
        return false;
    }

    /*
    This is the callback function for permission requests
    If permission is provided, start scanning for bluetooth low energy devices
    https://www.youtube.com/watch?v=exsvuXbk_2U
    */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == requestCodePermission && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanLeDevice();
        }
    }

    /*
    starts the scanner for BLE devices
    https://www.youtube.com/watch?v=exsvuXbk_2U
     */
    private void scanLeDevice() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        // check again if the correct permissions are present
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissions, requestCodePermission);
            return;
        }

        // if the app should be scanning for devices right now start scanning
        // otherwise turn the scanner of to conserve battery life
        if (!scanningEnd) {
            mBluetoothLeScanner.startScan(leScanCallback);
        } else {
            mBluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    /*
    this function is called whenever a new device is discovered by the BLE scanner
    partially used code from https://www.youtube.com/watch?v=exsvuXbk_2U
    */
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            if (!scanningEnd) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(permissions, requestCodePermission);
                    return;
                }

                // check if the discovered device is not known yet
                if (!capturedAddresses.contains(result.getDevice().getAddress() + ": " + result.getDevice().getName())) {
                    // if not, add it to the known devices and to the second fragment's log
                    capturedAddresses.add(result.getDevice().getAddress() + ": " + result.getDevice().getName());
                    secondFragment.scanLog(capturedAddresses);
                }

                // if the device has the correct name, connect to it
                if (result.getDevice().getName().equals("HCL Bracelet")) {
                    // scanning should be ended
                    scanningEnd = true;
                    // update the second fragment with the name and address of the connected device
                    secondFragment.setData(result.getDevice().getName() + ": " + result.getDevice().getAddress());

                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(permissions, requestCodePermission);
                        return;
                    }

                    // stop the BLE scanner because it drains the battery quickly
                    mBluetoothLeScanner.stopScan(leScanCallback);
                    // connect to the Gatt server of the device
                    result.getDevice().connectGatt(MainActivity.this, true, mGattCallback);
                }
            }
        }

        /*
        This is the callback for everything that happens between the Gatt server on the bracelet and the app
        partially used code from https://www.youtube.com/watch?v=exsvuXbk_2U
        */
        private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
            // If the connection changes (connect or disconnect) this function is called
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // If the app connects to the Gatt server on the bracelet, look for the services on that server
                    // Also set the the global mBluetoothGatt to this Gatt server so it is accessible outside of this function
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(permissions, requestCodePermission);
                        return;
                    }
                    gatt.discoverServices();
                    mBluetoothGatt = gatt;

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                    // If the app and Gatt server disconnect
                    scanningEnd = false;
                    disconnectBLE();
                    gatt.close();
                    mBluetoothGatt = null;
                    scanLeDevice();
                }
            }

            // onServicesDiscovered() handles the subscribing to a notify service
            // When services are discovered, get the service with the service uuid in bracelet.ino (here defined as NOTIFY_SERVICE)
            // Then get the Characteristic attached to that service with the characteristic uuid defined in bracelet.ino (here defined as NOTIFY_CHAR)
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                characteristicNotify = gatt.getService(NOTIFY_SERVICE).getCharacteristic(NOTIFY_CHAR);

                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(permissions, requestCodePermission);
                    return;
                }

                // subscribe to the notify characteristic
                gatt.setCharacteristicNotification(characteristicNotify, true);
            }

            // Whenever the bracelet changes a value and notifies the app, this function is called
            @Override
            public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
                super.onCharacteristicChanged(gatt, characteristic, value);
                // check if the correct characteristic has send the notify
                if(characteristic.equals(characteristicNotify)){
                    try {
                        // try to save the incoming data to the output file
                        commitToFile(value[0], value[1], value[2], value[3]);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("'Date: 'dd-MM-yyyy HH:mm:ss");
                    String currentDateAndTime = sdf.format(new Date());
                    final String entryString = currentDateAndTime + ", Heart rate: " + value[0] + ", O2: " + value[1] + ", Motion: " + value[2] + ", Temperature: " + value[3] + "\n";
                    // sent the new data to the first fragment so it can be displayed after the next update is called
                    firstFragment.setData(value[0], value[1], value[2], value[3], entryString);
                }
            }
        };

        // If the scan fails to get a valid uuid before it times out throw an error
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    /*
    This makes sure that the bluetooth connection is cleanly severed
    Otherwise it could take a bit before the app realizes it isn't connected anymore
    */
    private void disconnectBLE(){
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissions, requestCodePermission);
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /*
    Every time something that would need permission is used you need to check to see if you actually have those permissions
    This functions checks if the permissions are present
    https://www.youtube.com/watch?v=exsvuXbk_2U
    */
    private boolean hasPermissions(Context context, String[] permissions){
        for (String permission:permissions){
            if(ActivityCompat.checkSelfPermission(context,permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }

    /*
    This function takes the parameters and formats it into a string together with the time and date
    It then appends it to the output file
    */
    private void commitToFile(int hR, int o2, int mT, int tP) throws IOException {
        // make the date format
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("'Date: 'dd-MM-yyyy HH:mm:ss");
        // get the time and date in the previous format
        String currentDateAndTime = sdf.format(new Date());
        // put the output string together
        final String entryString = currentDateAndTime + ", Heart rate: " + hR + ", O2: " + o2 + ", Motion: " + mT + ", Temperature: " + tP + "\n";
        // open the file in append mode so the output gets added instead of overwriting
        FileOutputStream fOut = openFileOutput("SensorData1.txt", Context.MODE_APPEND);
        // make a file writer for the file
        OutputStreamWriter outputWriter = new OutputStreamWriter(fOut);
        // append the output text and close the connection
        outputWriter.append(entryString);
        outputWriter.flush();
        outputWriter.close();
    }

    /*
    This function puts a divider into the output file. this helps track where in the file possible gaps in data are
    */
    private void commitToFile() throws IOException {
        FileOutputStream fOut = openFileOutput("SensorData1.txt", Context.MODE_APPEND);
        OutputStreamWriter outputWriter = new OutputStreamWriter(fOut);
        outputWriter.append("----\n");
        outputWriter.flush();
        outputWriter.close();
    }

    /*
    This function returns what is written in the output file
    */
    public String readFromFile() throws IOException {
        String out = "";
        // open the file
        InputStream inputStream = openFileInput("SensorData1.txt");
        // if the file is found then make a file reader for it
        if(inputStream != null){
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String temp = "";
            // build the text from the document into a string
            StringBuilder stringBuilder = new StringBuilder();
            while((temp = bufferedReader.readLine()) != null){
                stringBuilder.append(temp);
                stringBuilder.append("\n");
            }
            // close the stream and give the received string back
            inputStream.close();
            out = stringBuilder.toString();
        }
        return out;
    }
}