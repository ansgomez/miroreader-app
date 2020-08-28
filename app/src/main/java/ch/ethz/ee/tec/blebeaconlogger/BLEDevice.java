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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.ParcelUuid;
import android.os.SystemClock;

public class BLEDevice {
    protected static final String TAG = "BLEDevice";

    protected final int DATA_SIZE = 14;
    protected String name;
    protected String address;
    protected String data;
    protected byte[] data_raw;
    protected long timestamp = 0;
    protected int rssi = 999;


    public BLEDevice(BLEDevice device) {
        this.name = device.name;
        this.address = device.address;
        this.data = device.data;
        this.timestamp = device.timestamp;
        this.rssi = device.rssi;
    }

    public BLEDevice(String name, String address, String data) {
        this.name = name;
        this.address = address;
        this.data = data;
    }

    public BLEDevice(BluetoothDevice device) {
        this.name = device.getName();
        this.address = device.getAddress();
        this.data = "";

        ParcelUuid[] uuids = device.getUuids();
        if (uuids != null) {
            for (int i = 0; i < uuids.length; i++) {
                if (i > 0) {
                    this.data += ":";
                }
                this.data += uuids[i].toString();
            }
        }
    }

    public BLEDevice(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        ScanRecord record = result.getScanRecord();
        this.name = device.getName();
        this.address = device.getAddress();
        this.timestamp = System.nanoTime() - SystemClock.elapsedRealtimeNanos() + result.getTimestampNanos();
        this.rssi = result.getRssi();
        this.data_raw = record.getBytes();

        this.data = "";
        int data_block_end = 0;
        for (int i = 0; i < this.data_raw.length; i++) {
            // ckeck for another block in packet
            if (i == data_block_end) {
                // zero length -> no additional data
                if (this.data_raw[i] == 0) {
                    break;
                }
                data_block_end = i + this.data_raw[i] + 1;
                // if not first packet add block separator
                if (i > 0) {
                    this.data += ",";
                }
            }
            this.data += String.format("%02x", this.data_raw[i]);
        }
    }

    public String getAddress() {
        return address;
    }

    public String getData() {
        return data;
    }

    public byte[] getDataRaw() {
        // Header: 3bytes + Timestamp: 4 bytes + MSGTYPE: 1 bytes
        if (data_raw.length < 9) {
            return null;
        }
        byte[] data_block = new byte[DATA_SIZE];
        for (int i = 0; i < data_block.length; i++) {
            data_block[i] = this.data_raw[3 + i];
        }
        return data_block;
    }

    public String getName() {
        return name;
    }

    public int getRssi() {
        return rssi;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
