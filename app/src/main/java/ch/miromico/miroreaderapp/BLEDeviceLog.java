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

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class BLEDeviceLog {
    protected static final String TAG = "BLEDeviceLog";

    private Character CSV_DELIMITER = ';';

    private ArrayList<BLEDevice> devices = new ArrayList<>();

    private ArrayList<BLEDevice> deviceHistory = new ArrayList<>();

    private long deviceMaxAge = 50000000000L;

    private File logFile = null;

    private FileWriter fileWriter = null;

    /**
     * Constructor does not take any arguments
     */
    public BLEDeviceLog() {
    }

    /**
     * Add a new BLE device observed
     *
     * @param bleDevice
     */
    public void add(BLEDevice bleDevice) {
//        deviceHistory.add(bleDevice);

        if (logFile != null) {
            writeLog(bleDevice);
        }

        updateDeviceList(bleDevice);
    }

    /**
     * Clear the log
     */
    public void clear() {
        this.devices.clear();
        this.deviceHistory.clear();
    }

    /**
     * Enable/Disable logging to file
     *
     * @param filename The file to write the log to
     */
    public void enableFileLogging(File filename) {
        if (fileWriter != null) {
            Log.e(TAG, "Error setting filename: still logging");
            return;
        }

        this.logFile = filename;
        try {
            fileWriter = new FileWriter(logFile);
            Log.i(TAG, "Opened log file: " + logFile.toString());
        } catch (IOException e) {
            Log.e(TAG, "Error opening output file: " + e.getMessage());
            return;
        }
        writeHeader();
    }

    public void disableFileLogging() {
        // disable if running
        if (fileWriter != null) {
            try {
                fileWriter.close();
                fileWriter = null;
                Log.i(TAG, "Closed log file: " + logFile.toString());
            } catch (IOException e) {
                Log.e(TAG, "Error closing output file: " + e.getMessage());
            }
        }

    }

    public void setDeviceMaxAge(int seconds) {
        this.deviceMaxAge = (long) 1e9 * (long) seconds;
    }

    /**
     * Get the full history of devices
     *
     * @return List of all devices observations
     */
    public ArrayList<BLEDevice> getDeviceHistory() {
        return this.deviceHistory;
    }

    /**
     * Get the list of available devices
     *
     * @return List of unique devices
     */
    public ArrayList<BLEDevice> getDeviceList() {
        // cleanup old devices before returning current list
        cleanupDeviceList();
        return devices;
    }

    private void cleanupDeviceList() {
        int i = devices.size() - 1;
        while (i >= 0) {
            BLEDevice d = devices.get(i);

            // drop device entry, if too old
            if (d.getTimestamp() > 0 && this.deviceMaxAge > 0) {
                long age = System.nanoTime() - d.getTimestamp();
                if (age > this.deviceMaxAge) {
                    devices.remove(i);
                    Log.d(TAG, "drop device: " + d.getAddress());
                }
            }
            // iterate
            i = i - 1;
        }
    }

    private void updateDeviceList(BLEDevice bleDevice) {
        // check for replacement
        int i = devices.size() - 1;
        while (i >= 0) {
            BLEDevice d = devices.get(i);

            // replace existing device entry, identified by address
            if (d.address.equals(bleDevice.address)) {
                devices.set(i, bleDevice);
                break;
            }

            // iterate
            i = i - 1;
        }

        // add if not replaced
        if (i < 0) {
            devices.add(bleDevice);
            Log.d(TAG, "new device: " + bleDevice.getAddress());
        }

        // drop old devices
        cleanupDeviceList();
    }

    private void writeHeader() {
        // skip writing if file unavailable
        if (fileWriter == null) {
            return;
        }

        // prepare data to write
        String headerRow = "";
        // 1) timestamp
        headerRow += "time";
        // 2) address
        headerRow += CSV_DELIMITER + "address";
        // 3) RSSI
        headerRow += CSV_DELIMITER + "RSSI";
        // 4) data
        headerRow += CSV_DELIMITER + "data";
        // 5) name
        headerRow += CSV_DELIMITER + "name";

        // write data to the file
        try {
            fileWriter.write(headerRow + "\n");
        } catch (IOException e) {
            Log.e(TAG, "Error writing output file: " + e.getMessage());
        }
    }

    private void writeLog(BLEDevice device) {
        // skip writing if file unavailable
        if (fileWriter == null) {
            return;
        }

        String address = device.getAddress();
        String data = device.getData();
        String name = device.getName();

        // prepare data to write
        String dataRow = "";
        // 1) timestamp
        dataRow += String.valueOf(device.getTimestamp());
        // 2) address
        if (address != null) {
            dataRow += CSV_DELIMITER + address;
        } else {
            dataRow += CSV_DELIMITER + "NA";
        }
        // 3) RSSI
        dataRow += CSV_DELIMITER + String.valueOf(device.getRssi());
        // 4) data
        if (data != null) {
            dataRow += CSV_DELIMITER + data;
        } else {
            dataRow += CSV_DELIMITER + "NA";
        }
        // 5) name
        if (name != null) {
            dataRow += CSV_DELIMITER + name;
        } else {
            dataRow += CSV_DELIMITER + "NA";
        }

        // write data to the file
        try {
            fileWriter.write(dataRow + "\n");
        } catch (IOException e) {
            Log.e(TAG, "Error writing output file: " + e.getMessage());
        }
    }
}
