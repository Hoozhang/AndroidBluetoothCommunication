package com.zhao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // 请求打开蓝牙
    private static final int REQUEST_ENABLE_BT = 200;

    // UUID
    public static final String BT_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    // Bluetooth Adapter
    private static BluetoothAdapter btAdapter;

    // 服务端线程
    private AcceptThread acceptThread;

    private Button bt_search;
    private ListView lv_device;
    private Button bt_connect;
    private Button bt_send;
    private EditText et_send;
    private TextView tv_receive;

    private List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private String targetDeviceAddr = "E4:26:8B:6F:A9:A9";

    private InputStream btIs;
    private OutputStream btOs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bt_search = (Button)findViewById(R.id.bt_search);
        lv_device = (ListView)findViewById(R.id.lv_device);
        bt_connect = (Button)findViewById(R.id.bt_connect);
        bt_send = (Button)findViewById(R.id.bt_send);
        et_send = (EditText)findViewById(R.id.et_send);
        tv_receive = (TextView)findViewById(R.id.tv_receive);

        // Initializes Bluetooth adapter
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if(btAdapter == null) {
            Log.e(TAG, "Search: Device doesn't support Bluetooth");
        }
        // Enable Bluetooth
        assert btAdapter != null;
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        // 开始接收线程
        acceptThread = new AcceptThread();
        acceptThread.start();
        Log.e(TAG, "Started Accept Thread");

        bt_search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Scan Paired Devices
                pairedDevices.clear();
                pairedDevices.addAll(btAdapter.getBondedDevices());
                // add ListView
                List<String> deviceNameAddr = new ArrayList<>();
                for (BluetoothDevice device : pairedDevices) {
                    deviceNameAddr.add(device.getName() + ": " + device.getAddress());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, deviceNameAddr);
                lv_device.setAdapter(adapter);
                showToast("搜索完毕!");
            }
        });

        lv_device.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                targetDeviceAddr = pairedDevices.get(position).getAddress();
                showToast("选择设备: " + pairedDevices.get(position).getName());
            }
        });

        bt_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectDevice();
            }
        });

        bt_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = et_send.getText().toString();
                if (!btAdapter.isEnabled()) {
                    showToast("BlueTooth is closed.");
                }
                if (!TextUtils.isEmpty(message)) {
                    write(message);
                    et_send.setText("");
                } else {
                    showToast("Text is empty.");
                }
            }
        });

    }

    private class AcceptThread extends Thread {

        private final BluetoothServerSocket btServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = btAdapter.listenUsingRfcommWithServiceRecord("TEST", UUID.fromString(BT_UUID));
            } catch (IOException e) {
                System.out.println("Socket's listen() method failed " + e);
            }
            btServerSocket = tmp;
        }

        public void run() {
            try {
                BluetoothSocket btSocket = btServerSocket.accept();
                btIs = btSocket.getInputStream();
                btOs = btSocket.getOutputStream();
                while (true) {
                    synchronized (this) {
                        byte[] byteArray = new byte[btIs.available()];
                        if (byteArray.length > 0) {
                            btIs.read(byteArray, 0, byteArray.length);
                            String content = new String(byteArray, "GBK");
                            Message msg = new Message();
                            msg.obj = content;
                            handler.sendMessage(msg);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void connectDevice() {
        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
        }
        try {
            BluetoothDevice btDevice = btAdapter.getRemoteDevice(targetDeviceAddr);
            BluetoothSocket btSocket = btDevice.createRfcommSocketToServiceRecord(UUID.fromString(BT_UUID));
            btSocket.connect();
            btOs = btSocket.getOutputStream();
            Log.e(TAG, "Connect Success");
            showToast("连接成功!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            tv_receive.append("\n" + msg.obj.toString());
            Log.e(TAG, "Receive" + msg.obj.toString());
            showToast("接收数据: " + msg.obj.toString());
            super.handleMessage(msg);
        }
    };

    public void write(String message) {
        try {
            if (btOs != null) {
                btOs.write(message.getBytes("GBK"));
            }
            Log.e(TAG, "Send" + message);
            showToast("发送数据: " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showToast(String message) {
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
    }
}