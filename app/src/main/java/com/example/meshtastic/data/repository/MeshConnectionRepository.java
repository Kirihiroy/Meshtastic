package com.example.meshtastic.data.repository;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.meshtastic.bluetooth.BleManager;
import com.example.meshtastic.data.model.NodeInfo;
import com.example.meshtastic.data.parser.MeshProtoParser;

import org.meshtastic.proto.MeshProtos;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единая точка управления BLE соединением с Meshtastic.
 * Хранит состояние и позволяет нескольким экранам наблюдать за ним через LiveData.
 */
public class MeshConnectionRepository {

    public enum State {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private static MeshConnectionRepository instance;

    public static synchronized MeshConnectionRepository getInstance(Context context) {
        if (instance == null) {
            instance = new MeshConnectionRepository(context.getApplicationContext());
        }
        return instance;
    }

    private final BleManager bleManager;

    private final MutableLiveData<State> state = new MutableLiveData<>(State.DISCONNECTED);
    private final MutableLiveData<String> statusText = new MutableLiveData<>("Не подключено");
    private final MutableLiveData<List<BluetoothDevice>> devices = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<BluetoothDevice> selectedDevice = new MutableLiveData<>(null);
    private final MutableLiveData<byte[]> lastRx = new MutableLiveData<>(null);
    private final MutableLiveData<String> lastFromRadioSummary = new MutableLiveData<>(null);
    private final MutableLiveData<List<NodeInfo>> nodes = new MutableLiveData<>(new ArrayList<>());

    private final Map<Long, NodeInfo> nodeMap = new ConcurrentHashMap<>();

    private final Set<String> seenAddresses = new HashSet<>();

    private MeshConnectionRepository(Context context) {
        bleManager = new BleManager(context);
    }

    public LiveData<State> getState() {
        return state;
    }

    public LiveData<String> getStatusText() {
        return statusText;
    }

    public LiveData<List<BluetoothDevice>> getDevices() {
        return devices;
    }

    public LiveData<BluetoothDevice> getSelectedDevice() {
        return selectedDevice;
    }

    public LiveData<byte[]> getLastRx() {
        return lastRx;
    }

    public LiveData<String> getLastFromRadioSummary() {
        return lastFromRadioSummary;
    }

    public LiveData<List<NodeInfo>> getNodes() {
        return nodes;
    }

    public boolean isBluetoothEnabled() {
        return bleManager.isBluetoothEnabled();
    }

    public void startScan() {
        state.postValue(State.SCANNING);
        statusText.postValue("Сканирование BLE…");
        devices.postValue(new ArrayList<>());
        seenAddresses.clear();

        bleManager.startScan(new BleManager.ScanListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device) {
                if (device == null || device.getAddress() == null) return;
                if (seenAddresses.contains(device.getAddress())) return;
                seenAddresses.add(device.getAddress());

                List<BluetoothDevice> current = devices.getValue();
                if (current == null) current = new ArrayList<>();
                current = new ArrayList<>(current);
                current.add(device);
                devices.postValue(current);

                if (selectedDevice.getValue() == null) {
                    selectedDevice.postValue(device);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                state.postValue(State.ERROR);
                statusText.postValue("Ошибка сканирования: " + errorCode);
            }
        });
    }

    public void stopScan() {
        bleManager.stopScan();
        if (state.getValue() == State.SCANNING) {
            state.postValue(State.DISCONNECTED);
            statusText.postValue("Сканирование остановлено");
        }
    }

    public void selectDevice(BluetoothDevice device) {
        selectedDevice.postValue(device);
    }

    public void connect() {
        BluetoothDevice device = selectedDevice.getValue();
        if (device == null) {
            state.postValue(State.ERROR);
            statusText.postValue("Устройство не выбрано");
            return;
        }

        state.postValue(State.CONNECTING);
        statusText.postValue("Подключение к " + safeName(device) + "…");

        bleManager.connect(device, new BleManager.ConnectionListener() {
            @Override
            public void onConnected() {
                state.postValue(State.CONNECTED);
                statusText.postValue("Подключено: " + safeName(device));
            }

            @Override
            public void onDisconnected() {
                state.postValue(State.DISCONNECTED);
                statusText.postValue("Отключено");
            }

            @Override
            public void onError(String message) {
                state.postValue(State.ERROR);
                statusText.postValue(message != null ? message : "Ошибка");
            }
        }, data -> {
            lastRx.postValue(data);
            handleFromRadio(data);
        });
    }

    public void disconnect() {
        bleManager.disconnect();
        state.postValue(State.DISCONNECTED);
        statusText.postValue("Отключено");
    }

    public boolean write(byte[] data) {
        return bleManager.write(data);
    }

    private static String safeName(BluetoothDevice d) {
        String n = d.getName();
        return (n == null || n.isEmpty()) ? d.getAddress() : n;
    }

    private void handleFromRadio(byte[] data) {
        if (data == null || data.length == 0) return;
        MeshProtos.FromRadio msg;
        try {
            msg = MeshProtos.FromRadio.parseFrom(data);
        } catch (Exception e) {
            return;
        }

        String summary = MeshProtoParser.parseFromRadioSummary(data);
        if (summary != null) {
            lastFromRadioSummary.postValue(summary);
        }

        switch (msg.getPayloadVariantCase()) {
            case NODE_INFO:
                MeshProtos.NodeInfo ni = msg.getNodeInfo();
                NodeInfo model = convertNode(ni);
                nodeMap.put(model.getNodeNum(), model);
                nodes.postValue(new ArrayList<>(nodeMap.values()));
                break;
            case MY_INFO:
                // Можно обновить статус по id узла
                statusText.postValue("Подключено: my_num=" + msg.getMyInfo().getMyNodeNum());
                break;
            default:
                break;
        }
    }

    private NodeInfo convertNode(MeshProtos.NodeInfo ni) {
        NodeInfo n = new NodeInfo();
        n.setNodeNum(ni.getNum() & 0xffffffffL);
        if (ni.hasUser()) {
            MeshProtos.User u = ni.getUser();
            n.setUserId(u.getId());
            n.setLongName(u.getLongName());
            n.setShortName(u.getShortName());
        }
        if (ni.hasPosition()) {
            MeshProtos.Position p = ni.getPosition();
            if (p.hasLatitudeI()) n.setLatitude(p.getLatitudeI() / 1e7);
            if (p.hasLongitudeI()) n.setLongitude(p.getLongitudeI() / 1e7);
        }
        n.setSnr(ni.getSnr());
        n.setLastHeard(ni.getLastHeard());
        if (ni.hasDeviceMetrics() && ni.getDeviceMetrics().hasBatteryLevel()) {
            n.setBatteryLevel(ni.getDeviceMetrics().getBatteryLevel());
        }
        if (ni.hasHopsAway()) n.setHopsAway(ni.getHopsAway());
        if (ni.hasChannel()) n.setChannel(ni.getChannel());
        n.setViaMqtt(ni.getViaMqtt());
        return n;
    }
}

