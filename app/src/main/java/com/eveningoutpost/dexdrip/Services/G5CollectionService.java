package com.eveningoutpost.dexdrip.Services;

/**
 * Created by jcostik1 on 3/15/16.
 */

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.PowerManager;

import com.eveningoutpost.dexdrip.G5Model.AuthChallengeRxMessage;
import com.eveningoutpost.dexdrip.G5Model.AuthChallengeTxMessage;

import com.eveningoutpost.dexdrip.G5Model.AuthRequestTxMessage;
import com.eveningoutpost.dexdrip.G5Model.AuthStatusRxMessage;
import com.eveningoutpost.dexdrip.G5Model.BluetoothServices;
import com.eveningoutpost.dexdrip.G5Model.Extensions;
import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.G5Model.Transmitter;

import com.eveningoutpost.dexdrip.UtilityModels.ForegroundServiceStarter;
import com.eveningoutpost.dexdrip.utils.BgToSpeech;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class G5CollectionService extends Service{

    private final static String TAG = G5CollectionService.class.getSimpleName();
    private ForegroundServiceStarter foregroundServiceStarter;

    public Service service;
    private BgToSpeech bgToSpeech;

    private PendingIntent pendingIntent;

    private final static int REQUEST_ENABLE_BT = 1;

    private android.bluetooth.BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLEScanner;
    private BluetoothGatt mGatt;
    private Transmitter defaultTransmitter;
    public AuthStatusRxMessage authStatus = null;
    public AuthRequestTxMessage authRequest = null;

    private ScanSettings settings;
    private List<ScanFilter> filters;

    private Handler handler;


    @Override
    public void onCreate() {
        super.onCreate();
//        readData = new ReadDataShare(this);
        service = this;
        foregroundServiceStarter = new ForegroundServiceStarter(getApplicationContext(), service);
        foregroundServiceStarter.start();
//        final IntentFilter bondintent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
//        registerReceiver(mPairReceiver, bondintent);
//        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
//        listenForChangeInSettings();
        bgToSpeech = BgToSpeech.setupTTS(getApplicationContext()); //keep reference to not being garbage collected
        handler = new Handler(getApplicationContext().getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(POWER_SERVICE);
//        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DexShareCollectionStart");
//        wakeLock.acquire(40000);
        Log.d(TAG, "onG5StartCommand");

        defaultTransmitter = new Transmitter("4023Q2");

        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        setupBluetooth();
        //}





//        try {
//
//            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
//                stopSelf();
//                return START_NOT_STICKY;
//            }
//            if (CollectionServiceStarter.isBTShare(getApplicationContext())) {
//                setFailoverTimer();
//            } else {
//                stopSelf();
//                return START_NOT_STICKY;
//            }
//            if (Sensor.currentSensor() == null) {
//                setRetryTimer();
//                return START_NOT_STICKY;
//            }
//            Log.i(TAG, "STARTING SERVICE");
//            attemptConnection();
//        } finally {
//            if(wakeLock != null && wakeLock.isHeld()) wakeLock.release();
//        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//        close();
//        setRetryTimer();
//        foregroundServiceStarter.stop();
//        unregisterReceiver(mPairReceiver);
//        BgToSpeech.tearDownTTS();
        Log.i(TAG, "SERVICE STOPPED");
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void setupBluetooth() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //First time using the app or bluetooth was turned off?
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        }
        else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<>();
                //Only look for CGM.
                filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID.fromString(BluetoothServices.Advertisement))).build());
            }
            startScan();
        }
    }

    public void stopScan() {
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
            else {
                mLEScanner.stopScan(mScanCallback);
            }
        }
    }

    public void startScan() {
        if (Build.VERSION.SDK_INT < 21) {
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
        else {
            Log.d(TAG, "startScan");
            mLEScanner.startScan(filters, settings, mScanCallback);
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            android.util.Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();

            // Check if the device has a name, the Dexcom transmitter always should. Match it with the transmitter id that was entered.
            // We get the last 2 characters to connect to the correct transmitter if there is more than 1 active or in the room.
            // If they match, connect to the device.
            if (btDevice.getName() != null) {
                String transmitterIdLastTwo = Extensions.lastTwoCharactersOfString(defaultTransmitter.transmitterId);
                String deviceNameLastTwo = Extensions.lastTwoCharactersOfString(btDevice.getName());

                if (transmitterIdLastTwo.equals(deviceNameLastTwo)) {
                    //They match, connect to the device.
                    connectToDevice(btDevice);
                } else {
                    startScan();
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                android.util.Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            android.util.Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private void runOnUiThread(Runnable r) {
        handler.post(r);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            // Check if the device has a name, the Dexcom transmitter always should. Match it with the transmitter id that was entered.
                            // We get the last 2 characters to connect to the correct transmitter if there is more than 1 active or in the room.
                            // If they match, connect to the device.
                            if (device.getName() != null) {
                                String transmitterIdLastTwo = Extensions.lastTwoCharactersOfString(defaultTransmitter.transmitterId);
                                String deviceNameLastTwo = Extensions.lastTwoCharactersOfString(device.getName());

                                if (transmitterIdLastTwo.equals(deviceNameLastTwo)) {
                                    //They match, connect to the device.
                                    connectToDevice(device);
                                }
                            }
                        }
                    });
                }
            };

    private void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(getApplicationContext(), false, gattCallback);
            stopScan();
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    android.util.Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    android.util.Log.e("gattCallback", "STATE_DISCONNECTED");
                    startScan();
                    break;
                default:
                    android.util.Log.e("gattCallback", "STATE_OTHER");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService cgmService = gatt.getService(UUID.fromString(BluetoothServices.CGMService));
            android.util.Log.i("onServiceDiscovered", cgmService.getUuid().toString());

            if (authStatus != null && authStatus.authenticated == 1) {
                BluetoothGattCharacteristic controlCharacteristic = cgmService.getCharacteristic(UUID.fromString(BluetoothServices.Control));
                if (!mGatt.readCharacteristic(controlCharacteristic)) {
                    android.util.Log.e("onCharacteristicRead", "ReadCharacteristicError");
                }
            }
            else {
                BluetoothGattCharacteristic authCharacteristic = cgmService.getCharacteristic(UUID.fromString(BluetoothServices.Authentication));
                if (!mGatt.readCharacteristic(authCharacteristic)) {
                    android.util.Log.e("onCharacteristicRead", "ReadCharacteristicError");
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (authStatus != null) {
                    if (authStatus.authenticated != 1) {
                        BluetoothGattCharacteristic authCharacteristic = mGatt.getService(UUID.fromString(BluetoothServices.CGMService)).getCharacteristic(UUID.fromString(BluetoothServices.Authentication));

                        AuthChallengeRxMessage authChallenge = new AuthChallengeRxMessage(authCharacteristic.getValue());
                        Log.i("AuthChallenge", Arrays.toString(authChallenge.challenge));
                        Log.i("AuthChallenge", Arrays.toString(authChallenge.tokenHash));
                    }
                    else if (authStatus.bonded == 5) {
                        Log.i("CharBytes", Arrays.toString(characteristic.getValue()));
                        Log.i("CharHex", Extensions.bytesToHex(characteristic.getValue()));


                    }
                }
            }

            mGatt.setCharacteristicNotification(characteristic, false);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.i("CharBytes", Arrays.toString(characteristic.getValue()));
                android.util.Log.i("CharHex", Extensions.bytesToHex(characteristic.getValue()));

                // Request authentication.
                AuthStatusRxMessage authStatus = new AuthStatusRxMessage(characteristic.getValue());
                if (authStatus.authenticated == 1 && authStatus.bonded == 1) {
                    android.util.Log.i("Auth", "Transmitter already authenticated");
                }
                else {
                    mGatt.setCharacteristicNotification(characteristic, true);

                    AuthRequestTxMessage authRequest = new AuthRequestTxMessage();
                    characteristic.setValue(authRequest.data.array());
                    mGatt.writeCharacteristic(characteristic);
                }
            }
        }
    };
}


//package com.eveningoutpost.dexdrip.Services;
//
//        import android.annotation.TargetApi;
//        import android.app.AlarmManager;
//        import android.app.PendingIntent;
//        import android.app.Service;
//        import android.bluetooth.BluetoothAdapter;
//        import android.bluetooth.BluetoothDevice;
//        import android.bluetooth.BluetoothGatt;
//        import android.bluetooth.BluetoothGattCallback;
//        import android.bluetooth.BluetoothGattCharacteristic;
//        import android.bluetooth.BluetoothGattDescriptor;
//        import android.bluetooth.BluetoothGattService;
//        import android.bluetooth.BluetoothManager;
//        import android.bluetooth.BluetoothProfile;
//        import android.content.BroadcastReceiver;
//        import android.content.Context;
//        import android.content.Intent;
//        import android.content.IntentFilter;
//        import android.content.SharedPreferences;
//        import android.os.Build;
//        import android.os.IBinder;
//        import android.os.PowerManager;
//        import android.preference.PreferenceManager;
//        import com.eveningoutpost.dexdrip.Models.UserError.Log;
//
//        import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.ReadDataShare;
//        import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.CalRecord;
//        import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.EGVRecord;
//        import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.records.SensorRecord;
//        import com.eveningoutpost.dexdrip.Models.ActiveBluetoothDevice;
//        import com.eveningoutpost.dexdrip.Models.BgReading;
//        import com.eveningoutpost.dexdrip.Models.Calibration;
//        import com.eveningoutpost.dexdrip.Models.Sensor;
//        import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
//        import com.eveningoutpost.dexdrip.UtilityModels.DexShareAttributes;
//        import com.eveningoutpost.dexdrip.UtilityModels.ForegroundServiceStarter;
//        import com.eveningoutpost.dexdrip.UtilityModels.HM10Attributes;
//        import com.eveningoutpost.dexdrip.utils.BgToSpeech;
//
//        import java.nio.charset.StandardCharsets;
//        import java.util.Calendar;
//        import java.util.Date;
//        import java.util.List;
//        import java.util.UUID;
//
//        import rx.Observable;
//        import rx.functions.Action1;
//
//@TargetApi(Build.VERSION_CODES.KITKAT)
//public class DexShareCollectionService extends Service {

//
//    public SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
//        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
//            if(key.compareTo("run_service_in_foreground") == 0) {
//                Log.d("FOREGROUND", "run_service_in_foreground changed!");
//                if (prefs.getBoolean("run_service_in_foreground", false)) {
//                    foregroundServiceStarter = new ForegroundServiceStarter(getApplicationContext(), service);
//                    foregroundServiceStarter.start();
//                    Log.i(TAG, "Moving to foreground");
//                } else {
//                    service.stopForeground(true);
//                    Log.i(TAG, "Removing from foreground");
//                }
//            }
//        }
//    };
//
//    public void listenForChangeInSettings() {
//        prefs.registerOnSharedPreferenceChangeListener(prefListener);
//    }
//
//    public void setRetryTimer() {
//        if (CollectionServiceStarter.isBTShare(getApplicationContext())) {
//            BgReading bgReading = BgReading.last();
//            long retry_in;
//            if (bgReading != null) {
//                retry_in = Math.min(Math.max((1000 * 30), (1000 * 60 * 5) - (new Date().getTime() - bgReading.timestamp) + (1000 * 5)), (1000 * 60 * 5));
//            } else {
//                retry_in = (1000 * 20);
//            }
//            Log.d(TAG, "Restarting in: " + (retry_in / (60 * 1000)) + " minutes");
//            Calendar calendar = Calendar.getInstance();
//            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
//            if (pendingIntent != null)
//                alarm.cancel(pendingIntent);
//            long wakeTime = calendar.getTimeInMillis() + retry_in;
//            pendingIntent = PendingIntent.getService(this, 0, new Intent(this, this.getClass()), 0);
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
//            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                alarm.setExact(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
//            } else
//                alarm.set(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
//        }
//    }
//
//    public void setFailoverTimer() { //Sometimes it gets stuck in limbo on 4.4, this should make it try again
//        if (CollectionServiceStarter.isBTShare(getApplicationContext())) {
//            long retry_in = (1000 * 60 * 5);
//            Log.d(TAG, "Fallover Restarting in: " + (retry_in / (60 * 1000)) + " minutes");
//            Calendar calendar = Calendar.getInstance();
//            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
//            if (pendingIntent != null)
//                alarm.cancel(pendingIntent);
//            long wakeTime = calendar.getTimeInMillis() + retry_in;
//            pendingIntent = PendingIntent.getService(this, 0, new Intent(this, this.getClass()), 0);
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
//            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                alarm.setExact(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
//            } else
//                alarm.set(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent);
//        } else {
//            stopSelf();
//        }
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        throw new UnsupportedOperationException("Not yet implemented");
//    }
//
//    public void attemptConnection() {
//        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
//        if (mBluetoothManager != null) {
//            if (device != null) {
//                mConnectionState = STATE_DISCONNECTED;
//                for (BluetoothDevice bluetoothDevice : mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT)) {
//                    if (bluetoothDevice.getAddress().compareTo(device.getAddress()) == 0) {
//                        mConnectionState = STATE_CONNECTED;
//                    }
//                }
//            }
//            Log.i(TAG, "Connection state: " + mConnectionState);
//            if (mConnectionState == STATE_DISCONNECTED || mConnectionState == STATE_DISCONNECTING) {
//                ActiveBluetoothDevice btDevice = ActiveBluetoothDevice.first();
//                if (btDevice != null) {
//                    mDeviceName = btDevice.name;
//                    mDeviceAddress = btDevice.address;
//                    mBluetoothAdapter = mBluetoothManager.getAdapter();
//                    if (mBluetoothAdapter.isEnabled() && mBluetoothAdapter.getRemoteDevice(mDeviceAddress) != null) {
//                        connect(mDeviceAddress);
//                        return;
//                    } else {
//                        Log.w(TAG, "Bluetooth is disabled or BT device cant be found");
//                        setRetryTimer();
//                        return;
//                    }
//                } else {
//                    Log.w(TAG, "No bluetooth device to try and connect to");
//                    setRetryTimer();
//                    return;
//                }
//            } else if (mConnectionState == STATE_CONNECTED) {
//                Log.i(TAG, "Looks like we are already connected, going to read!");
//                attemptRead();
//                return;
//            } else {
//                setRetryTimer();
//                return;
//            }
//        } else {
//            setRetryTimer();
//            return;
//        }
//    }
//
//    public void requestHighPriority() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mBluetoothGatt != null) {
//            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
//        }
//    }
//
//    public void requestLowPriority() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mBluetoothGatt != null) {
//            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER);
//        }
//    }
//
//    public void attemptRead() {
//        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
//        final PowerManager.WakeLock wakeLock1 = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
//                "ReadingShareData");
//        wakeLock1.acquire(60000);
//        requestHighPriority();
//        Log.d(TAG, "Attempting to read data");
//        final Action1<Long> systemTimeListener = new Action1<Long>() {
//            @Override
//            public void call(Long s) {
//                if (s != null) {
//                    Log.d(TAG, "Made the full round trip, got " + s + " as the system time");
//                    final long additiveSystemTimeOffset = new Date().getTime() - s;
//
//                    final Action1<Long> dislpayTimeListener = new Action1<Long>() {
//                        @Override
//                        public void call(Long s) {
//                            if (s != null) {
//                                Log.d(TAG, "Made the full round trip, got " + s + " as the display time offset");
//                                final long addativeDisplayTimeOffset = additiveSystemTimeOffset - (s * 1000);
//
//                                Log.d(TAG, "Making " + addativeDisplayTimeOffset + " the the total time offset");
//
//                                final Action1<EGVRecord[]> evgRecordListener = new Action1<EGVRecord[]>() {
//                                    @Override
//                                    public void call(EGVRecord[] egvRecords) {
//                                        if (egvRecords != null) {
//                                            Log.d(TAG, "Made the full round trip, got " + egvRecords.length + " EVG Records");
//                                            BgReading.create(egvRecords, additiveSystemTimeOffset, getApplicationContext());
//                                            {
//                                                Log.d(TAG, "Releasing wl in egv");
//                                                if(wakeLock1 != null && wakeLock1.isHeld()) wakeLock1.release();
//                                                requestLowPriority();
//                                                Log.d(TAG, "released");
//                                            }
//                                            if (shouldDisconnect) {
//                                                stopSelf();
//                                            } else {
//                                                setRetryTimer();
//                                            }
//                                        }
//                                    }
//                                };
//
//                                final Action1<SensorRecord[]> sensorRecordListener = new Action1<SensorRecord[]>() {
//                                    @Override
//                                    public void call(SensorRecord[] sensorRecords) {
//                                        if (sensorRecords != null) {
//                                            Log.d(TAG, "Made the full round trip, got " + sensorRecords.length + " Sensor Records");
//                                            BgReading.create(sensorRecords, additiveSystemTimeOffset, getApplicationContext());
//                                            readData.getRecentEGVs(evgRecordListener);
//                                        }
//                                    }
//                                };
//
//                                final Action1<CalRecord[]> calRecordListener = new Action1<CalRecord[]>() {
//                                    @Override
//                                    public void call(CalRecord[] calRecords) {
//                                        if (calRecords != null) {
//                                            Log.d(TAG, "Made the full round trip, got " + calRecords.length + " Cal Records");
//                                            Calibration.create(calRecords, addativeDisplayTimeOffset, getApplicationContext());
//                                            readData.getRecentSensorRecords(sensorRecordListener);
//                                        }
//                                    }
//                                };
//                                readData.getRecentCalRecords(calRecordListener);
//                            } else
//                            if(wakeLock1 != null && wakeLock1.isHeld()) wakeLock1.release();
//                        }
//                    };
//                    readData.readDisplayTimeOffset(dislpayTimeListener);
//                } else
//                if(wakeLock1 != null && wakeLock1.isHeld()) wakeLock1.release();
//
//            }
//        };
//        readData.readSystemTime(systemTimeListener);
//    }
//
//    public boolean connect(final String address) {
//        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(POWER_SERVICE);
//        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
//                "DexShareCollectionStart");
//        wakeLock.acquire(30000);
//        Log.i(TAG, "going to connect to device at address" + address);
//        if (mBluetoothAdapter == null || address == null) {
//            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
//            setRetryTimer();
//            return false;
//        }
//        if (mBluetoothGatt != null) {
//            Log.i(TAG, "BGatt isnt null, Closing.");
//            mBluetoothGatt.close();
//            mBluetoothGatt = null;
//        }
//        for (BluetoothDevice bluetoothDevice : mBluetoothAdapter.getBondedDevices()) {
//            if (bluetoothDevice.getAddress().compareTo(address) == 0) {
//                Log.v(TAG, "Device found, already bonded, going to connect");
//                if(mBluetoothAdapter.getRemoteDevice(bluetoothDevice.getAddress()) != null) {
//                    device = bluetoothDevice;
//                    mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mGattCallback);
//                    return true;
//                }
//            }
//        }
//        device = mBluetoothAdapter.getRemoteDevice(address);
//        if (device == null) {
//            Log.w(TAG, "Device not found.  Unable to connect.");
//            setRetryTimer();
//            return false;
//        }
//        Log.i(TAG, "Trying to create a new connection.");
//        mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mGattCallback);
//        mConnectionState = STATE_CONNECTING;
//        return true;
//    }
//
//    public void authenticateConnection() {
//        Log.i(TAG, "Trying to auth");
//        String receiverSn = prefs.getString("share_key", "SM00000000").toUpperCase() + "000000";
//        if(receiverSn.compareTo("SM00000000000000") == 0) { // They havnt set their serial number, dont bond!
//            setRetryTimer();
//            return;
//        }
//        byte[] bondkey = (receiverSn).getBytes(StandardCharsets.US_ASCII);
//        if (mBluetoothGatt != null) {
//            if (mShareService != null) {
//                if(!share2) {
//                    mAuthenticationCharacteristic = mShareService.getCharacteristic(DexShareAttributes.AuthenticationCode);
//                } else {
//                    mAuthenticationCharacteristic = mShareService.getCharacteristic(DexShareAttributes.AuthenticationCode2);
//                }
//                if (mAuthenticationCharacteristic != null) {
//                    Log.v(TAG, "Auth Characteristic found: " + mAuthenticationCharacteristic.toString());
//                    if (mAuthenticationCharacteristic.setValue(bondkey)) {
//                        mBluetoothGatt.writeCharacteristic(mAuthenticationCharacteristic);
//                    } else {
//                        setRetryTimer();
//                    }
//                } else {
//                    Log.w(TAG, "Authentication Characteristic IS NULL");
//                    setRetryTimer();
//                }
//            } else {
//                Log.w(TAG, "CRADLE SERVICE IS NULL");
//            }
//        } else {
//            setRetryTimer();
//        }
//    }
//
//    public void assignCharacteristics() {
//        if(!share2) {
//            Log.d(TAG, "Setting #1 characteristics");
//            mSendDataCharacteristic = mShareService.getCharacteristic(DexShareAttributes.ShareMessageReceiver);
//            mReceiveDataCharacteristic = mShareService.getCharacteristic(DexShareAttributes.ShareMessageResponse);
//            mCommandCharacteristic = mShareService.getCharacteristic(DexShareAttributes.Command);
//            mResponseCharacteristic = mShareService.getCharacteristic(DexShareAttributes.Response);
//            mHeartBeatCharacteristic = mShareService.getCharacteristic(DexShareAttributes.HeartBeat);
//        } else {
//            Log.d(TAG, "Setting #1 characteristics");
//            mSendDataCharacteristic = mShareService.getCharacteristic(DexShareAttributes.ShareMessageReceiver2);
//            mReceiveDataCharacteristic = mShareService.getCharacteristic(DexShareAttributes.ShareMessageResponse2);
//            mCommandCharacteristic = mShareService.getCharacteristic(DexShareAttributes.Command2);
//            mResponseCharacteristic = mShareService.getCharacteristic(DexShareAttributes.Response2);
//            mHeartBeatCharacteristic = mShareService.getCharacteristic(DexShareAttributes.HeartBeat2);
//        }
//    }
//
//    public void setListeners(int listener_number) {
//        Log.i(TAG, "Setting Listener: #" + listener_number);
//        if (listener_number == 1) {
//            step = 2;
//            setCharacteristicIndication(mReceiveDataCharacteristic);
//        } else {
//            step = 3;
//            attemptRead();
//        }
//    }
//
//
//    public void close() {
//        if (mBluetoothGatt == null) {
//            return;
//        }
//        mBluetoothGatt.close();
//        setRetryTimer();
//        mBluetoothGatt = null;
//        mConnectionState = STATE_DISCONNECTED;
//        Log.i(TAG, "bt Disconnected");
//    }
//
//    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic) {
//        setCharacteristicNotification(characteristic, true);
//    }
//
//    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
//        Log.i(TAG, "Characteristic setting notification");
//        if (mBluetoothGatt != null) {
//            mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
//            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(HM10Attributes.CLIENT_CHARACTERISTIC_CONFIG));
//            Log.i(TAG, "Descriptor found: " + descriptor.getUuid());
//            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//            mBluetoothGatt.writeDescriptor(descriptor);
//        }
//    }
//
//    public void setCharacteristicIndication(BluetoothGattCharacteristic characteristic) {
//        setCharacteristicIndication(characteristic, true);
//    }
//
//    public void setCharacteristicIndication(BluetoothGattCharacteristic characteristic, boolean enabled) {
//        Log.i(TAG, "Characteristic setting indication");
//        if (mBluetoothGatt != null) {
//            mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
//            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(HM10Attributes.CLIENT_CHARACTERISTIC_CONFIG));
//            Log.i(TAG, "Descriptor found: " + descriptor.getUuid());
//            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
//            mBluetoothGatt.writeDescriptor(descriptor);
//        }
//    }
//
//    public void writeCommand(List<byte[]> packets, int aRecordType, Action1<byte[]> dataResponseListener) {
//        mDataResponseListener = dataResponseListener;
//        successfulWrites = 0;
//        writePackets = packets;
//        recordType = aRecordType;
//        step = 0;
//        currentGattTask = GATT_WRITING_COMMANDS;
//        gattWritingStep();
//    }
//
//    public void clearGattTask() {
//        currentGattTask = GATT_NOTHING;
//        step = 0;
//    }
//
//    private void gattSetupStep() {
//        step = 1;
//        if(share2) { assignCharacteristics(); }
//        setListeners(1);
//    }
//
//    private void gattWritingStep() {
//        Log.d(TAG, "Writing command to the Gatt, step: " + step);
//        int index = step;
//        if (index <= (writePackets.size() - 1)) {
//            Log.d(TAG, "Writing: " + writePackets.get(index) + " index: " + index);
//            if(mSendDataCharacteristic != null && writePackets != null) {
//                mSendDataCharacteristic.setValue(writePackets.get(index));
//                if (mBluetoothGatt != null && mBluetoothGatt.writeCharacteristic(mSendDataCharacteristic)) {
//                    Log.d(TAG, "Wrote Successfully");
//                }
//            }
//        } else {
//            Log.d(TAG, "Done Writing commands");
//            clearGattTask();
//        }
//    }
//
//    private final BroadcastReceiver mPairReceiver = new BroadcastReceiver() {
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            final BluetoothDevice bondDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//
//            if (mBluetoothGatt != null && mBluetoothGatt.getDevice() != null && bondDevice != null) {
//                if (!bondDevice.getAddress().equals(mBluetoothGatt.getDevice().getAddress())) {
//                    Log.d(TAG, "Bond state wrong device");
//                    return; // That wasnt a device we care about!!
//                }
//            }
//
//            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
//                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
//                if (state == BluetoothDevice.BOND_BONDED) {
//                    authenticateConnection();
//                }
//            }
//        }
//    };
//
//    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
//        @Override
//        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//            Log.i(TAG, "Gatt state change status: " + status + " new state: " + newState);
//            if (status == 133) {
//                Log.e(TAG, "Got the status 133 bug, bad news! Might require devices to forget each other");
//            }
//            if (newState == BluetoothProfile.STATE_CONNECTED) {
//                mBluetoothGatt = gatt;
//                device = mBluetoothGatt.getDevice();
//                mConnectionState = STATE_CONNECTED;
//                ActiveBluetoothDevice.connected();
//                Log.i(TAG, "Connected to GATT server.");
//
//                Log.i(TAG, "discovering services");
//                currentGattTask = GATT_SETUP;
//                if (mBluetoothGatt == null || !mBluetoothGatt.discoverServices()) {
//                    Log.w(TAG, "discovering failed");
//                    if(shouldDisconnect) {
//                        stopSelf();
//                    } else {
//                        setRetryTimer();
//                    }
//                }
//            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
//                mConnectionState = STATE_DISCONNECTED;
//                ActiveBluetoothDevice.disconnected();
//                if(shouldDisconnect) {
//                    stopSelf();
//                } else {
//                    setRetryTimer();
//                }
//                Log.d(TAG, "Disconnected from GATT server.");
//            } else {
//                Log.d(TAG, "Gatt callback... strange state.");
//            }
//        }
//
//        @Override
//        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
//            Log.d(TAG, "services discovered " + status);
//            if (status == BluetoothGatt.GATT_SUCCESS && mBluetoothGatt != null) {
//                mShareService = mBluetoothGatt.getService(DexShareAttributes.CradleService);
//                if(mShareService == null) {
//                    mShareService = mBluetoothGatt.getService(DexShareAttributes.CradleService2);
//                    share2 = true;
//                } else {
//                    share2 = false;
//                }
//                assignCharacteristics();
//                authenticateConnection();
//                gattSetupStep();
//            } else {
//                Log.w(TAG, "No Services Discovered!");
//            }
//        }
//
//        @Override
//        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                Log.v(TAG, "Characteristic Read " + characteristic.getUuid());
//                if(mHeartBeatCharacteristic.getUuid().equals(characteristic.getUuid())) {
//                    Log.v(TAG, "Characteristic Read " + characteristic.getUuid() + " " + characteristic.getValue());
//                    setCharacteristicNotification(mHeartBeatCharacteristic);
//                }
//                gatt.readCharacteristic(mHeartBeatCharacteristic);
//            } else {
//                Log.e(TAG, "Characteristic failed to read");
//            }
//        }
//
//        @Override
//        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
//            UUID charUuid = characteristic.getUuid();
//            Log.d(TAG, "Characteristic Update Received: " + charUuid);
//            if (charUuid.compareTo(mReceiveDataCharacteristic.getUuid()) == 0) {
//                Log.d(TAG, "mCharReceiveData Update");
//                byte[] value = characteristic.getValue();
//                if (value != null) {
//                    Observable.just(characteristic.getValue()).subscribe(mDataResponseListener);
//                }
//            } else if (charUuid.compareTo(mHeartBeatCharacteristic.getUuid()) == 0) {
//                long heartbeat = System.currentTimeMillis();
//                Log.d(TAG, "Heartbeat delta: " + (heartbeat - lastHeartbeat));
//                if ((heartbeat-lastHeartbeat < 59000) || heartbeatCount > 5) {
//                    Log.d(TAG, "Early heartbeat.  Fetching data.");
//                    AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
//                    alarm.cancel(pendingIntent);
//                    heartbeatCount = 0;
//                    attemptConnection();
//                }
//                heartbeatCount += 1;
//                lastHeartbeat = heartbeat;
//            }
//        }
//
//        @Override
//        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
//                Log.d(TAG, "Characteristic onDescriptorWrite ch " + characteristic.getUuid());
//                if(mHeartBeatCharacteristic.getUuid().equals(characteristic.getUuid())) {
//                    state_notifSetupSucess = true;
//                    setCharacteristicIndication(mReceiveDataCharacteristic);
//                }
//                if(mReceiveDataCharacteristic.getUuid().equals(characteristic.getUuid())) {
//                    setCharacteristicIndication(mResponseCharacteristic);
//                }
//                if(mResponseCharacteristic.getUuid().equals(characteristic.getUuid())) {
//                    attemptRead();
//                }
//            } else if ((status & BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) != 0 || (status & BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) != 0) {
//                if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_NONE) {
//                    device = gatt.getDevice();
//                    state_authInProgress = true;
//                    bondDevice();
//                } else {
//                    Log.e(TAG, "The phone is trying to read from paired device without encryption. Android Bug? Have the dexcom forget whatever device it was previously paired to");
//                }
//            } else {
//                Log.e(TAG, "Unknown error writing descriptor");
//            }
//        }
//
//        @Override
//        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
//            Log.d(TAG, "characteristic wrote " + status);
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                Log.d(TAG, "Wrote a characteristic successfully " + characteristic.getUuid());
//                if (mAuthenticationCharacteristic.getUuid().equals(characteristic.getUuid())) {
//                    state_authSucess = true;
//                    gatt.readCharacteristic(mHeartBeatCharacteristic);
//                }
//            } else if ((status & BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) != 0 || (status & BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) != 0) {
//                if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_NONE) {
//                    device = gatt.getDevice();
//                    state_authInProgress = true;
//                    bondDevice();
//                } else {
//                    Log.e(TAG, "The phone is trying to read from paired device without encryption. Android Bug? Have the dexcom forget whatever device it was previously paired to");
//                }
//            } else {
//                Log.e(TAG, "Unknown error writing Characteristic");
//            }
//        }
//    };
//
//    public void bondDevice() {
//        final IntentFilter bondintent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
//        registerReceiver(mPairReceiver, bondintent);
//        if(!share2){ device.setPin("000000".getBytes()); }
//        device.createBond();
//    }
//}