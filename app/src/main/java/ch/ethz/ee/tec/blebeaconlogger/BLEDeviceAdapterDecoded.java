package ch.ethz.ee.tec.blebeaconlogger;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

public class BLEDeviceAdapterDecoded extends RecyclerView.Adapter<BLEDeviceAdapterDecoded.ViewHolder> {
    protected static final String TAG = "BLEDeviceAdapterDecoded";

    public static final int ALL     = 0xFF;
    public static final int TEMP_RH = 0x01;
    public static final int LIGHT   = 0x02;
    public static final int ACC     = 0x04;

    /**
     * List of Bluetooth Low Energy devices
     */
    protected List<BLEDevice> devicesList;

    public BLEDeviceAdapterDecoded(List<BLEDevice> devices) {
        this.devicesList = devices;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.device_row_decoded, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        BLEDevice device = devicesList.get(position);

        String address = device.getAddress();
        byte[] data = device.getDataRaw();

        String name = device.getName();

        if (address != null) {
            holder.address.setText(address);
        } else {
            holder.address.setText("{no addr}");
        }
        if (data == null) {
            holder.temperature.setText("N/A");
            holder.humidity.setText("N/A");
            holder.time.setText("N/A");
        } else if (data.length > 5) {
            Log.d(TAG, data.toString());
            char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
            char[] hexChars = new char[data.length * 2];
            for (int j = 0; j < data.length; j++) {
                int v = data[j] & 0xFF;
                hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            Log.d(TAG, new String(hexChars));

            int timestamp_raw = ((data[0] & 0xFF) | ((data[1] & 0xFF) << 8)
                                | ((data[2] & 0xFF) << 16) | ((data[3] & 0xFF) << 24));

            String message = "Found tiemstamp" + timestamp_raw;
            Log.d(TAG, message);

            if(timestamp_raw != 0xFAFBFCFD) {
                name = "Andres Gomez Miro Card";
                holder.temperature.setText("Account");
                holder.humidity.setText("created:");
                holder.luminosity.setText("13.08.2020");
                holder.accX.setText("");
                holder.accY.setText("");
                holder.accZ.setText("");
                holder.time.setText("Gold Costumer");
            }
            else if(timestamp_raw != 0xABABABAB) {
                //OLD DATA FORMAT
                int humidity_raw = (data[4] & 0xFF) | (((int) data[5] & 0x03) << 8);
                int temperature_raw = ((data[5] & 0xFC) >> 2) | ((data[6] & 0xFF) << 6);

                // conversion
                float temperature = -40.0f + (float) temperature_raw / 100.0f;
                float humidity = (float) humidity_raw / 10.0f;

                holder.temperature.setText(String.format("%+7.2f °C", temperature));
                holder.humidity.setText(String.format("%5.1f %%RH", humidity));
                holder.time.setText(String.format("%08x", timestamp_raw));
            }
            else {
                //NEW DATA FORMAT
                int messageType = (data[4] & 0xFF);

                // decode and convert data
                if ((messageType & TEMP_RH) == TEMP_RH) {
                    int humidity_raw = (data[5] & 0xFF) | (((int) data[6] & 0x03) << 8);
                    int temperature_raw = ((data[6] & 0xFC) >> 2) | ((data[7] & 0xFF) << 6);
                    float temperature = -40.0f + (float) temperature_raw / 100.0f;
                    float humidity = (float) humidity_raw / 10.0f;
                    holder.temperature.setText(String.format("%+7.2f °C", temperature));
                    holder.humidity.setText(String.format("%5.1f %%RH", humidity));
                }
                if ((messageType & LIGHT) == LIGHT) {
                    int light_raw = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
                    float light = (float) light_raw / 10;
                    holder.luminosity.setText(String.format("%.1f lx", light));
                }
                if ((messageType & ACC) == ACC) {
                    int accX_raw = (data[10] & 0xFF) | ((data[11] & 0x03) << 8);
                    int accY_raw = ((data[11] & 0xFF) >> 2) | ((data[12] & 0xF0) << 2);
                    int accZ_raw = ((data[12] & 0x0F)) | ((data[13] & 0xFF) << 4);
                    float accX = -2.0f + (float) (accX_raw) / 100.0f;
                    float accY = -2.0f + (float) (accY_raw) / 100.0f;
                    float accZ = -2.0f + (float) (accZ_raw) / 100.0f;
                    holder.accX.setText(String.format("X:%.2f g", accX));
                    holder.accY.setText(String.format("Y:%.2f g", accY));
                    holder.accZ.setText(String.format("Z:%.2f g", accZ));
                }

                holder.time.setText(String.format("%08x", timestamp_raw));
            }
        } else {
            holder.temperature.setText("err");
            holder.humidity.setText("err");
            holder.luminosity.setText("err");
            holder.accX.setText("err");
            holder.accY.setText("err");
            holder.accZ.setText("err");
            holder.time.setText("err");
        }

        if (name != null) {
            holder.name.setText(name);
        } else {
            holder.name.setText("{no name}");
        }
        holder.rssi.setText(String.format("%d", device.getRssi()));
        holder.timestamp.setText(String.format("%d", device.getTimestamp()));
    }

    @Override
    public int getItemCount() {
        return devicesList.size();
    }

    /**
     * Provide a reference to the type of views that you are using (custom ViewHolder)
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView name, address, temperature, humidity, luminosity, accX, accY, accZ, time, rssi, timestamp;

        public ViewHolder(View view) {
            super(view);

            name = view.findViewById(R.id.name);
            address = view.findViewById(R.id.address);
            temperature = view.findViewById(R.id.temperature);
            humidity = view.findViewById(R.id.humidity);
            luminosity = view.findViewById(R.id.luminosity);
            accX = view.findViewById(R.id.accX);
            accY = view.findViewById(R.id.accY);
            accZ = view.findViewById(R.id.accZ);
            time = view.findViewById(R.id.time);
            rssi = view.findViewById(R.id.rssi);
            timestamp = view.findViewById(R.id.timestamp);

            // Define click listener for the ViewHolder's View.
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.i(TAG, "Clicked list item " + address.getText());

                    String deviceName = name.getText() + " - " + address.getText();
                    String deviceDetail = temperature.getText() + "\n" + humidity.getText() +
                            "\n" + rssi.getText() + "\n" + timestamp.getText();

                    // Use the Builder class for convenient dialog construction
                    AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                    builder.setTitle(deviceName);
                    builder.setMessage(deviceDetail);

                    // Create the AlertDialog object and return it
                    builder.show();
                }
            });
        }
    }
}
