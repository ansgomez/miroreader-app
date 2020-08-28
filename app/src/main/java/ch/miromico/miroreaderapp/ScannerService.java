/*
 *
 * Copyright (c) 2020, Andres Gomez, Miromico AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * Copyright (c) 2019, Swiss Federal Institute of Technology (ETH Zurich)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package ch.miromico.miroreaderapp;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

import ch.miromico.miroreaderapp.R;

public class ScannerService extends Service {
    protected static final String TAG = "ScannerService";
    /**
     * Android N max background scan interval of 30 min.
     * See also: https://github.com/AltBeacon/android-beacon-library/issues/512
     */
    protected static final long ANDROID_N_MAX_SCAN_DURATION = 30 * 60 * 1000l;
    private static final String NOTIFICATION_CHANNEL_ID = "ch.miromico.miroreaderapp.activity";

    private final int SCANNER_NOTIFICATION_ID = 1;
    /**
     * Local service binder
     */
    private final LocalBinder localBinder;
    /**
     * Notification builder
     */
    Notification.Builder notificationBuilder;
    NotificationManager notificationManager;

    /**
     * Handler for timed BLE device scan restarts
     */
    private Handler scanRestartHandler;

    /**
     * Scan type (if running)
     */
    private ScanType scanType;

    /**
     * Bluetooth adapter for scanning BLE devices
     */
    private BluetoothAdapter bluetoothAdapter;
    /**
     * Bluetooth Low Energy scanner structure
     */
    private BluetoothLeScanner bleScanner;
    /**
     * BLE Scanner settings
     */
    private ScanSettings bleScanSettings;
    /**
     * BLE Scanner filter
     */
    private ArrayList<ScanFilter> bleScanFilters;

    /**
     * BLE device log manager
     */
    private BLEDeviceLog deviceLog;
    /**
     * Bluetooth LE scan callback
     */
    private ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.v(TAG, result.getDevice().getAddress());
            deviceLog.add(new BLEDevice(result));

            // TODO notify bound activity to update UI if available
//            bleDeviceList = bleDeviceLog.getDeviceList();
//            deviceAdapter.notifyDataSetChanged();
        }
    };

    /**
     * Bluetooth LE scan restart timeout callback
     */
    private Runnable scanRestartTask = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "scanRestartTask()");

            // skip if no BLE scan is running
            if (scanType != ScanType.SCAN_BLE_DEVICE) {
                Log.i(TAG, "scanRestartTask: no scan running");
                return;
            }

            // restart the BLE scan
            restartScan();

            // schedule next restart of BLE scan
            scanRestartHandler.postDelayed(scanRestartTask, ANDROID_N_MAX_SCAN_DURATION / 2);
        }
    };

    /**
     * ScannerService class initialization
     */
    public ScannerService() {
        localBinder = new LocalBinder();
        scanType = ScanType.SCAN_IDLE;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");

        // create notification and register foreground service
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // create the NotificationChannel, necessary and available only for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, getString(R.string.scanner_notification_channel_name),
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription(getString(R.string.scanner_notification_channel_description));
            // register the channel with the system
            notificationManager.createNotificationChannel(channel);
        }

        notificationBuilder = new Notification.Builder(this);
        notificationBuilder.setContentTitle(getText(R.string.scanner_notification_title));
        notificationBuilder.setContentText(getText(R.string.scanner_notification_idle));
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher);
        notificationBuilder.setContentIntent(pendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder.setChannelId(NOTIFICATION_CHANNEL_ID);
        }

        Notification notification = notificationBuilder.build();
        startForeground(SCANNER_NOTIFICATION_ID, notification);

        // prepare logging
        deviceLog = new BLEDeviceLog();

        // initialize scanners and scanner settings
        initBleScanner();
        bleScanFilters = new ArrayList<>();

        // init BLE scan restart handler
        scanRestartHandler = new Handler();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");

        // stop foreground task and remove notification
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand()");

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        return localBinder;
    }

    /**
     * Initialize BLE device discovery scanner
     */
    private void initBleScanner() {
        Log.i(TAG, "initBleScanner()");

        // Ensures Bluetooth is available on the device and it is enabled. Notify user if not.
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // bluetooth not supported by the device
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Bluetooth support required");
            builder.setMessage("This app needs bluetooth support to work.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            // bluetooth not supported by the device
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Bluetooth disabled");
            builder.setMessage("Enable bluetooth first before starting this service.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
            return;
        }

        // get a BLE scanner object
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        setupScan(true);
    }

    /**
     * Setup the logging of BLE devices
     *
     * @param logFile      The file to store a log of all received BLE  packets
     * @param deviceMaxAge Max device age before removing from the list
     */
    public void setupLogging(File logFile, int deviceMaxAge) {
        Log.i(TAG, "setupLogging()");

        if (logFile != null) {
            deviceLog.enableFileLogging(logFile);
        } else {
            deviceLog.disableFileLogging();
        }

        deviceLog.setDeviceMaxAge(deviceMaxAge);
    }

    public void setupScan(boolean aggressiveScan) {
        Log.i(TAG, "setupScan()");

        // prepare default scan settings
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        if (aggressiveScan) {
            builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
        }
        builder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT);
        builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        builder.setReportDelay(0);
        bleScanSettings = builder.build();
    }

    public void setupFilter(ArrayList<String> bleMACs) {
        Log.i(TAG, "setupFilter()");

        bleScanFilters.clear();
        if (bleMACs == null) {
            return;
        }

        // create filter for mac addresses
        for (String bleMAC : bleMACs) {
            ScanFilter.Builder builder = new ScanFilter.Builder();
            builder.setDeviceAddress(bleMAC);
            bleScanFilters.add(builder.build());
        }
    }

    public void startScan(ScanType type) {
        Log.i(TAG, "startScan()");

        // idle scan cannot be started
        if (type == ScanType.SCAN_IDLE) {
            throw new IllegalArgumentException("cannot start idle scan");
        }

        // abort if scan is running
        if (scanType != ScanType.SCAN_IDLE) {
            throw new IllegalStateException("another scan is running already");
        }

        // clear log
        deviceLog.clear();

        // start corresponding scan
        switch (type) {
            case SCAN_BLE_DEVICE:
                // start scanning BLE devices
                bleScanner.startScan(bleScanFilters, bleScanSettings, bleScanCallback);
                notificationBuilder.setContentText(getText(R.string.scanner_notification_scan_device));
                Log.i(TAG, "started BLE device scan...");

                // schedule restart of BLE scan before system scan timeout
                scanRestartHandler.postDelayed(scanRestartTask, ANDROID_N_MAX_SCAN_DURATION / 2);
                break;

            case SCAN_BLUETOOTH:
                break;
        }

        scanType = type;
        notificationManager.notify(SCANNER_NOTIFICATION_ID, notificationBuilder.build());
    }

    public void stopScan() {
        Log.i(TAG, "stopScan()");

        // abort if no scan is running
        if (scanType == ScanType.SCAN_IDLE) {
            throw new IllegalStateException("no active scan");
        }

        // start corresponding scan
        switch (scanType) {
            case SCAN_BLE_DEVICE:
                bleScanner.stopScan(bleScanCallback);
                Log.i(TAG, "stopped BLE device scan...");

                scanRestartHandler.removeCallbacks(scanRestartTask);
                break;

            case SCAN_BLUETOOTH:

                break;
        }

        // disable file logging to close open files
        deviceLog.disableFileLogging();

        scanType = ScanType.SCAN_IDLE;

        notificationBuilder.setContentText(getText(R.string.scanner_notification_idle));
        notificationManager.notify(SCANNER_NOTIFICATION_ID, notificationBuilder.build());
    }

    private void restartScan() {
        Log.i(TAG, "restartScan()");

        // abort if scan is running
        if (scanType != ScanType.SCAN_BLE_DEVICE) {
            throw new IllegalStateException("can only restart BLE device scan");
        }

        // stop and restart scanning BLE devices
        bleScanner.stopScan(bleScanCallback);
        bleScanner.startScan(bleScanFilters, bleScanSettings, bleScanCallback);
        Log.i(TAG, "restarted BLE device scan...");
    }

    public ScanType getScanStatus() {
        return scanType;
    }

    public ArrayList<BLEDevice> getActiveDeviceList() {
        return deviceLog.getDeviceList();
    }

    /**
     * Scan type enum
     */
    public enum ScanType {
        SCAN_IDLE,
        SCAN_BLE_DEVICE,
        SCAN_BLUETOOTH,
    }

    /**
     * Local service binder helper class
     */
    public class LocalBinder extends Binder {
        public ScannerService getService() {
            return ScannerService.this;
        }
    }

}
