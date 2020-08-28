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
