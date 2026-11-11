package io.github.walktheearth.txtlightstick;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/** Minimal GATT client for the lightstick's write-only LED characteristic. */
@SuppressLint("MissingPermission")
class BleLightStick {

    interface Listener {
        void onStatus(String message);

        void onReady(String name);

        void onDisconnected();
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic ledCharacteristic;
    private volatile boolean ready;
    private byte[] pending;
    private boolean writing;
    private boolean connecting;
    private Listener listener;
    private String deviceName = "";

    BleLightStick(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
    }

    boolean isReady() {
        return ready;
    }

    boolean isScanning() {
        return scanner != null;
    }

    boolean scanAndConnect(Listener listener) {
        this.listener = listener;
        if (adapter == null) {
            listener.onStatus("This device has no Bluetooth adapter");
            return false;
        }
        if (!adapter.isEnabled()) {
            listener.onStatus("Turn Bluetooth on, then tap Connect again");
            return false;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            listener.onStatus("Bluetooth LE is not available");
            return false;
        }
        connecting = false;
        scanner.startScan(scanCallback);
        listener.onStatus("Scanning for a lightstick...");
        return true;
    }

    void stopScan() {
        if (scanner != null) {
            scanner.stopScan(scanCallback);
            scanner = null;
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (connecting) return;
            ScanRecord record = result.getScanRecord();
            String name = result.getDevice().getName();
            if (name == null && record != null) name = record.getDeviceName();
            if (!Protocol.matches(name) && !Protocol.matches(result.getDevice().getAddress())) return;
            connecting = true;
            deviceName = name == null ? result.getDevice().getAddress() : name;
            stopScan();
            if (listener != null) listener.onStatus("Found " + deviceName + ", connecting...");
            connect(result.getDevice());
        }

        @Override
        public void onScanFailed(int errorCode) {
            if (listener != null) listener.onStatus("Bluetooth scan failed (code " + errorCode + ")");
            stopScan();
        }
    };

    private void connect(BluetoothDevice device) {
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        if (gatt == null && listener != null) {
            listener.onStatus("Could not start a GATT connection");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt connection, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                main.post(() -> {
                    if (listener != null) listener.onStatus("Connected, discovering services...");
                });
                connection.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                ready = false;
                ledCharacteristic = null;
                pending = null;
                writing = false;
                connecting = false;
                connection.close();
                if (gatt == connection) gatt = null;
                main.post(() -> {
                    if (listener != null) listener.onDisconnected();
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt connection, int status) {
            BluetoothGattService service = connection.getService(Protocol.SERVICE_UUID);
            BluetoothGattCharacteristic characteristic =
                    service == null ? null : service.getCharacteristic(Protocol.LED_MODE_UUID);
            if (characteristic == null) {
                main.post(() -> {
                    if (listener != null) listener.onStatus("Lightstick LED service not found");
                });
                connection.disconnect();
                return;
            }
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            ledCharacteristic = characteristic;
            ready = true;
            connecting = false;
            main.post(() -> {
                if (listener != null) listener.onReady(deviceName);
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS && listener != null) {
                main.post(() -> listener.onStatus("Colour write failed (status " + status + ")"));
            }
            writing = false;
            pump();
        }
    };

    /** Queue a packet; only the newest pending packet is kept. */
    void write(byte[] packet) {
        if (!ready) return;
        synchronized (this) {
            pending = packet;
        }
        pump();
    }

    private void pump() {
        byte[] packet;
        synchronized (this) {
            if (!ready || writing) return;
            packet = pending;
            pending = null;
            if (packet == null) return;
            writing = true;
        }
        BluetoothGatt connection = gatt;
        BluetoothGattCharacteristic characteristic = ledCharacteristic;
        boolean sent = false;
        if (connection != null && characteristic != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sent = connection.writeCharacteristic(
                        characteristic, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                        == BluetoothStatusCodes.SUCCESS;
            } else {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                characteristic.setValue(packet);
                sent = connection.writeCharacteristic(characteristic);
            }
        }
        if (!sent) {
            synchronized (this) {
                writing = false;
            }
        }
    }

    void disconnect() {
        ready = false;
        connecting = false;
        stopScan();
        BluetoothGatt connection = gatt;
        gatt = null;
        ledCharacteristic = null;
        pending = null;
        if (connection != null) {
            connection.disconnect();
            connection.close();
        }
        if (listener != null) listener.onDisconnected();
    }
}
