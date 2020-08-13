package ch.ethz.ee.tec.blebeaconlogger;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import java.util.List;

public class BLEDeviceAdapterRaw extends RecyclerView.Adapter<BLEDeviceAdapterRaw.ViewHolder> {
    protected static final String TAG = "BLEDeviceAdapterRaw";

    /**
     * List of Bluetooth Low Energy devices
     */
    protected List<BLEDevice> devicesList;

    public BLEDeviceAdapterRaw(List<BLEDevice> devices) {
        this.devicesList = devices;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.device_row_raw, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        BLEDevice device = devicesList.get(position);

        String address = device.getAddress();
        String data = device.getData();
        String name = device.getName();

        if (address != null) {
            holder.address.setText(address);
        } else {
            holder.address.setText("{no addr}");
        }
        if (data != null) {
            holder.data.setText(data);
        } else {
            holder.data.setText("{no data}");
        }
        if (name != null) {
            holder.name.setText(name);
        } else {
            holder.name.setText("{no name}");
        }
        holder.rssi.setText(String.format("%d", device.getRssi()));
        holder.timestamp.setText(String.format("%d", device.getTimestamp()));

        if ("18:04:ED:61:66:3D".equalsIgnoreCase(address.trim())) {
            //name = "Andres Gomez' Miro Card";
            holder.avatar.setImageResource(R.drawable.profile_ag);
        } else if ("18:04:ED:61:66:71".equalsIgnoreCase(address.trim())) {
            //name = "Kevin Luchsinger's Miro Card";
            holder.avatar.setImageResource(R.drawable.profile_kl);
        } else {
            holder.avatar.setImageResource(R.drawable.profile_default);
        }
    }

    @Override
    public int getItemCount() {
        return devicesList.size();
    }

    /**
     * Provide a reference to the type of views that you are using (custom ViewHolder)
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView name, address, data, rssi, timestamp;
        public ImageView avatar;

        public ViewHolder(View view) {
            super(view);

            name = view.findViewById(R.id.name);
            address = view.findViewById(R.id.address);
            data = view.findViewById(R.id.data);
            rssi = view.findViewById(R.id.rssi);
            timestamp = view.findViewById(R.id.timestamp);
            avatar = view.findViewById(R.id.imageViewAvatar);

            // Define click listener for the ViewHolder's View.
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.i(TAG, "Clicked list item " + view.toString());

                    String deviceName = name.getText() + " - " + address.getText();
                    String deviceDetail = data.getText() + "\n" + rssi.getText() + "\n" + timestamp.getText();

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
