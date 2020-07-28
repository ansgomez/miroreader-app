package ch.ethz.ee.tec.blebeaconlogger;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.ParcelUuid;
import android.os.SystemClock;

public class BLEDevice {
    protected static final String TAG = "BLEDevice";

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
        if (data_raw.length < 10) {
            return null;
        }
        byte[] data_block = new byte[7];
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
