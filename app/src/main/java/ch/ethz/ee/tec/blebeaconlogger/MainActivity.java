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

package ch.ethz.ee.tec.blebeaconlogger;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Spinner;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    protected static final String TAG = "MainActivity";
    /**
     * Callback callee identifiers for permission requests
     */
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 2;
    private static final int PERMISSION_REQUEST_BACKGROUND_LOCATION = 3;
    private static final int PERMISSION_REQUEST_BLUETOOTH_ADMIN = 4;
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 5;

    /**
     * Callback callee identifiers for permission requests
     */
    private static final int INTENT_REQUEST_BLUETOOTH_ENABLE = 1;
    /**
     * Application preferences
     */
    SharedPreferences preferences;
    /**
     * Application data directory
     */
    private File applicationDirectory;
    /**
     * BLE scanner service
     */
    private ScannerService scannerService;
    private boolean scannerServiceBound = false;
    /**
     * Handler for timed UI updates
     */
    private Handler updateHandler;
    /**
     * BLE decives list for recycler view
     */
    private ArrayList<BLEDevice> deviceList;
    /**
     * BLE device recycler view
     */
    private RecyclerView recyclerView;
    /**
     * BLE device list adapter
     */
    private RecyclerView.Adapter deviceAdapter;
    /**
     * UI updater runnable
     */
    private Runnable listUpdateTask = new Runnable() {
        @Override
        public void run() {
            if (!scannerServiceBound) {
                return;
            }

            // get device max age to display
            String defaultMaxAge = getResources().getString(R.string.pref_ui_max_age_default);
            int maxAge = Integer.parseInt(preferences.getString("pref_ui_max_age", defaultMaxAge));

            // update existing device list entries device list
            ArrayList<BLEDevice> newDeviceList = new ArrayList<BLEDevice>(scannerService.getActiveDeviceList());

            int i = deviceList.size() - 1;
            while (i >= 0) {
                BLEDevice device = deviceList.get(i);

                // check for existence and replacement
                int j = newDeviceList.size() - 1;
                while (j >= 0) {
                    BLEDevice newDevice = newDeviceList.get(j);
                    if (newDevice.getAddress() == device.getAddress()) {
                        if (newDevice.getTimestamp() > device.getTimestamp()) {
                            deviceList.set(i, new BLEDevice(newDevice));
                        }
                        newDeviceList.remove(j);
                        break;
                    }

                    // iterate
                    j = j - 1;
                }

                // check for removal
                if (j < 0) {
                    deviceList.remove(i);
                }

                // iterate
                i = i - 1;
            }

            // add remaining (i.e. new) devices
            deviceList.addAll(newDeviceList);

            // update list
            deviceAdapter.notifyDataSetChanged();

            // schedule next update if running scan
            if (scannerService.getScanStatus() != ScannerService.ScanType.SCAN_IDLE) {
                String defaultUpdateRate = getResources().getString(R.string.pref_ui_update_interval_default);
                int updateInterval = Integer.parseInt(preferences.getString("pref_ui_update_interval", defaultUpdateRate));
                updateHandler.postDelayed(listUpdateTask, updateInterval);
            }
        }
    };
    /**
     * Scanner service connection with callbacks
     */
    private ServiceConnection scannerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // get the background service instance via binder
            ScannerService.LocalBinder binder = (ScannerService.LocalBinder) iBinder;
            scannerService = binder.getService();
            scannerServiceBound = true;
            updateUiState();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            scannerServiceBound = false;
            updateUiState();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // check Bluetooth is available on the device
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // bluetooth not supported by the device
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Bluetooth support required");
            builder.setMessage("This app needs bluetooth support to work.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
            return;
        }

        // request permissions online for recent android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermissions();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // initialize log file storage
        prepareFileStorage();

        // connect to service if Bluetooth enabled, else request and do so on callback
        connectService();

        // get app shared preferences
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // prepare recycler view
        recyclerView = findViewById(R.id.recycler_view_devices);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(mLayoutManager);

        // init list update handler
        updateHandler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // initialize device list
        deviceList = new ArrayList<>();

        // check which adapter to use
        boolean defaultDecodeData = getResources().getBoolean(R.bool.pref_ui_decode_data_default);
        boolean decodeData = preferences.getBoolean("pref_ui_decode_data", defaultDecodeData);

        // setup list adapter
        if (decodeData) {
            deviceAdapter = new BLEDeviceAdapterDecoded(deviceList);
        } else {
            deviceAdapter = new BLEDeviceAdapterRaw(deviceList);
        }
        recyclerView.setAdapter(deviceAdapter);

        // UI state update
        updateUiState();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // unbind service before closing app
        if (scannerServiceBound) {
            unbindService(scannerServiceConnection);
            // stop service if no scan running (otherwise continue in background)
            if (scannerService.getScanStatus() == ScannerService.ScanType.SCAN_IDLE) {
                Intent intent = new Intent(this, ScannerService.class);
                stopService(intent);
            } else {
                Log.i(TAG, "Scanner running in background. Let it continue in background.");
            }
        } else {
            Log.i(TAG, "No background service bound. Forec stop potentially running service.");

            // stop potentially running service (not bound) to clean up
            Intent intent = new Intent(this, ScannerService.class);
            stopService(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // handle menu actions
        switch (id) {
            case R.id.action_settings:
                Intent setting_intent = new Intent(this, SettingsActivity.class);
                startActivity(setting_intent);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void checkPermissions() {
        // Android M location permission check , before coarse location suffices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // fine location is needed for Bluetooth scanning starting with API level 29
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can receive BLE beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                PERMISSION_REQUEST_FINE_LOCATION);
                    }
                });
                builder.show();
            } else {
                // check also for background location permission to receive beacons in background
                if (this.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("This app needs background location access");
                    builder.setMessage("Please grant background location access so this app can receive BLE beacons also in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                        }
                    });
                    builder.show();
                }
            }
        } else {
            // coarse location suffices for for Bluetooth scanning starting for API level 28 and lower
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can receive BLE beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }

        // Android M bluetooth permission check 
        if (this.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs to bluetooth admin access");
            builder.setMessage("Please grant bluetooth administration access to scan for BLE devices.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN}, PERMISSION_REQUEST_BLUETOOTH_ADMIN);
                }
            });
            builder.show();
        }

        // Android M storage permission check 
        if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs storage access");
            builder.setMessage("Please grant external storage write access for the logging functionality.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
                }
            });
            builder.show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;

            case PERMISSION_REQUEST_FINE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "fine location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;

            case PERMISSION_REQUEST_BACKGROUND_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "background location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;

            case PERMISSION_REQUEST_BLUETOOTH_ADMIN:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "bluetooth admin permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since bluetooth admin permission has not been granted, this app will not be able to discover any BLE devices.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;

            case PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "write external storage permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since writing storage permission has not been granted, this app will not be able to log any BLE devices.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case INTENT_REQUEST_BLUETOOTH_ENABLE:
                Log.i(TAG, "BT enable request result: " + Integer.toString(resultCode));
                connectService();
                return;
            default:
                Log.e(TAG, "Uncaught ActivityResult call");
                return;
        }
    }

    /**
     * Toggle BLE device discovery scan.
     *
     * @param view
     */
    public void toggleBleScan(View view) {
        ScannerService.ScanType scanStatus = scannerService.getScanStatus();
        if (scanStatus == ScannerService.ScanType.SCAN_IDLE) {
            setupScan("ble.csv");
            scannerService.startScan(ScannerService.ScanType.SCAN_BLE_DEVICE);
        } else if (scanStatus == ScannerService.ScanType.SCAN_BLE_DEVICE) {
            scannerService.stopScan();
        } else {
            throw new IllegalStateException("BLE device button should only be accessible if idle or device scanning");
        }

        updateUiState();
    }

    /**
     * Start and/or connect to (existing) Bluetooth scanner service.
     * <p>
     * Request enabling Bluetooth adapter if necessary and skips connecting service if doing so.
     * Another call to connectService() is required once the Bluetooth request is processed.
     */
    private void connectService() {
        // if bluetooth adapter not enabled, request if not and skip initialization
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, INTENT_REQUEST_BLUETOOTH_ENABLE);
            return;
        }

        // service connection
        if (!isServiceRunning(ScannerService.class)) {
            Intent intent = new Intent(this, ScannerService.class);
            startService(intent);
        }

        // bind service
        Intent intent = new Intent(this, ScannerService.class);
        boolean connected = bindService(intent, scannerServiceConnection, 0);
        if (connected) {
            Log.i(TAG, "service bound successfully");
        } else {
            Log.i(TAG, "service not found");
        }
    }

    /**
     * Initialize the storage for the log files
     */
    private void prepareFileStorage() {
        // get application directory and create if not available
        String appName = getResources().getString(R.string.app_name).replaceAll("\\s+", "");
        applicationDirectory = new File(Environment.getExternalStorageDirectory(), appName);
        Log.i(TAG, "app directory: " + applicationDirectory.toString());

        // create app directory if it does not exists
        if (!applicationDirectory.exists()) {
            if (!applicationDirectory.mkdir()) {
                Log.e(TAG, "failed to create directory: " + applicationDirectory.toString());
            }
        }
    }

    private void setupScan(String fileSuffix) {
        boolean defaultFileLoggingEnable = getResources().getBoolean(R.bool.pref_logging_file_enable_default);
        boolean fileLoggingEnable = preferences.getBoolean("pref_logging_file_enable", defaultFileLoggingEnable);
        File logFile = null;
        if (fileLoggingEnable) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            logFile = new File(applicationDirectory, timestamp + "_" + fileSuffix);
        }

        // configure logging parameters
        String defaultMaxAge = getResources().getString(R.string.pref_ui_max_age_default);
        int maxAge = Integer.parseInt(preferences.getString("pref_ui_max_age", defaultMaxAge));
        scannerService.setupLogging(logFile, maxAge);

        // configure scan mode
        boolean defaultAggressiveScan = getResources().getBoolean(R.bool.pref_logging_aggressive_scan_enable_default);
        boolean aggressiveScan = preferences.getBoolean("pref_logging_aggressive_scan_enable", defaultAggressiveScan);
        scannerService.setupScan(aggressiveScan);

        // configure scan filter
        Spinner ble_filter_spinner = findViewById(R.id.spinner_ble_filter);
        int filter_index = ble_filter_spinner.getSelectedItemPosition();
        String[] filter_values = getResources().getStringArray(R.array.ble_filter_values);

        ArrayList<String> filter_list = new ArrayList<>();
        switch (filter_values[filter_index]) {
            case "":
                filter_list = null;
                break;
            case "all":
                for (String filter : filter_values) {
                    if (BluetoothAdapter.checkBluetoothAddress(filter)) {
                        filter_list.add(filter);
                    }
                }
                break;
            default:
                filter_list.add(filter_values[filter_index]);
                break;
        }
        scannerService.setupFilter(filter_list);
    }

    private void updateUiState() {
        ScannerService.ScanType scanStatus = ScannerService.ScanType.SCAN_IDLE;
        if (scannerServiceBound) {
            scanStatus = scannerService.getScanStatus();
        }
        Button ble_button = findViewById(R.id.button_ble);
        Spinner ble_filter_spinner = findViewById(R.id.spinner_ble_filter);

        // restore default UI state
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ble_button.setText(R.string.start_ble_device_scan);
        ble_button.setEnabled(true);
        ble_filter_spinner.setEnabled(true);

        // apply state dependent UI changes
        switch (scanStatus) {
            case SCAN_BLE_DEVICE:
                ble_button.setText(R.string.stop_ble_device_scan);
                ble_filter_spinner.setEnabled(false);
                break;

            case SCAN_IDLE:
            default:
                break;
        }

        // screen settings
        if (scanStatus != ScannerService.ScanType.SCAN_IDLE) {
            boolean defaultKeepScreenOn = getResources().getBoolean(R.bool.pref_ui_keep_screen_on_default);
            boolean keepScreenOn = preferences.getBoolean("pref_ui_keep_screen_on", defaultKeepScreenOn);
            if (keepScreenOn) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            // clear device list
            deviceList.clear();

            // trigger list updater with minmal delay
            updateHandler.postDelayed(listUpdateTask, 100);
        } else {
            updateHandler.removeCallbacks(listUpdateTask);
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(serviceInfo.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}
