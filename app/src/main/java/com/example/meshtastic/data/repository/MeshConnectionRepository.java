package com.example.meshtastic.data.repository;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.meshtastic.bluetooth.BleManager;
import com.example.meshtastic.data.model.DeviceStatus;
import com.example.meshtastic.data.model.NodeInfo;
import com.example.meshtastic.data.parser.MeshProtoParser;

import org.meshtastic.proto.ChannelProtos;
import org.meshtastic.proto.MeshProtos;
import org.meshtastic.proto.Portnums;

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

    private int wantConfigId = 1;

    private final MutableLiveData<DeviceStatus> deviceStatus = new MutableLiveData<>(new DeviceStatus());

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

    public LiveData<DeviceStatus> getDeviceStatus() {
        return deviceStatus;
    }

    public boolean isBluetoothEnabled() {
        return bleManager.isBluetoothEnabled();
    }

    public void startScan() {
        state.postValue(State.SCANNING);
        statusText.postValue("Сканирование BLE…");
        updateDeviceStatus(s -> {
            s.setState(State.SCANNING.name());
            s.setStatusText("Сканирование BLE…");
        });
        devices.postValue(new ArrayList<>());
        seenAddresses.clear();

        bleManager.startScan(new BleManager.ScanListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi) {
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
            updateDeviceStatus(s -> {
                s.setState(State.ERROR.name());
                s.setStatusText("Устройство не выбрано");
            });
            return;
        }

        updateDeviceStatus(s -> {
            s.setState(State.CONNECTING.name());
            s.setDeviceName(safeName(device));
            s.setStatusText("Подключение к " + safeName(device) + "…");
        });

        state.postValue(State.CONNECTING);
        statusText.postValue("Подключение к " + safeName(device) + "…");

        bleManager.connect(device, new BleManager.ConnectionListener() {
            @Override
            public void onConnected() {
                state.postValue(State.CONNECTED);
                statusText.postValue("Подключено: " + safeName(device));
                updateDeviceStatus(s -> {
                    s.setState(State.CONNECTED.name());
                    s.setStatusText("Подключено: " + safeName(device));
                });
                // Сразу попросим конфиг/инфо, чтобы устройство начало отвечать FromRadio
                requestConfig();
            }

            @Override
            public void onDisconnected() {
                state.postValue(State.DISCONNECTED);
                statusText.postValue("Отключено");
                updateDeviceStatus(s -> {
                    s.setState(State.DISCONNECTED.name());
                    s.setStatusText("Отключено");
                });
            }

            @Override
            public void onError(String message) {
                state.postValue(State.ERROR);
                statusText.postValue(message != null ? message : "Ошибка");
                updateDeviceStatus(s -> {
                    s.setState(State.ERROR.name());
                    s.setStatusText(message != null ? message : "Ошибка");
                });
            }
        }, data -> {
            lastRx.postValue(data);
            updateDeviceStatus(s -> {
                s.setLastRxAt(System.currentTimeMillis());
                s.setLastRxHex(toHex(data));
            });
            handleFromRadio(data);
        });
    }

    public void disconnect() {
        bleManager.disconnect();
        state.postValue(State.DISCONNECTED);
        statusText.postValue("Отключено");
        // можно очистить список узлов при отключении
        nodeMap.clear();
        nodes.postValue(new ArrayList<>());
    }

    public boolean write(byte[] data) {
        if (data == null) return false;
        State st = state.getValue();
        if (st != State.CONNECTED) return false;
        bleManager.write(data);
        return true;
    }

    public boolean sendToRadio(MeshProtos.ToRadio msg) {
        if (msg == null) return false;
        State st = state.getValue();
        if (st != State.CONNECTED) return false;

        // BLE transport: чистый protobuf БЕЗ length-delimited framing
        // (length-delimited нужен только для Serial)
        byte[] raw = msg.toByteArray();

        bleManager.write(raw);
        return true;
    }

    public boolean applyChannelPsk(String channelName, String pskText) {
        if (channelName == null || channelName.trim().isEmpty()) return false;
        if (pskText == null || pskText.trim().isEmpty()) return false;
        if (state.getValue() != State.CONNECTED) return false;

        byte[] pskBytes = pskText.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ChannelProtos.ChannelSettings settings = ChannelProtos.ChannelSettings.newBuilder()
                .setName(channelName.trim())
                .setPsk(com.google.protobuf.ByteString.copyFrom(pskBytes))
                .build();

        ChannelProtos.Channel channel = ChannelProtos.Channel.newBuilder()
                .setIndex(0)
                .setRole(ChannelProtos.Channel.Role.PRIMARY)
                .setSettings(settings)
                .build();

        MeshProtos.FromRadio out = MeshProtos.FromRadio.newBuilder()
                .setChannel(channel)
                .build();

        MeshProtos.MeshPacket packet = MeshProtos.MeshPacket.newBuilder()
                .setDecoded(MeshProtos.Data.newBuilder()
                        .setPortnum(Portnums.PortNum.ADMIN_APP)
                        .setPayload(out.toByteString())
                        .build())
                .build();

        MeshProtos.ToRadio msg = MeshProtos.ToRadio.newBuilder()
                .setPacket(packet)
                .build();

        return sendToRadio(msg);
    }

    private static String safeName(BluetoothDevice d) {
        String n = d.getName();
        return (n == null || n.isEmpty()) ? d.getAddress() : n;
    }

    private void handleFromRadio(byte[] data) {
        if (data == null || data.length == 0) return;

        // FromRadio приходит как один protobuf (без varint length-prefix)
        MeshProtos.FromRadio msg;
        try {
            msg = MeshProtos.FromRadio.parseFrom(data);
        } catch (Exception e) {
            return;
        }

        String summary = MeshProtoParser.parseFromRadioSummary(data);
        if (summary != null) {
            lastFromRadioSummary.postValue(summary);
            updateDeviceStatus(s -> s.setLastSummary(summary));
        }

        switch (msg.getPayloadVariantCase()) {
            case NODE_INFO: {
                MeshProtos.NodeInfo ni = msg.getNodeInfo();
                NodeInfo model = convertNode(ni);
                nodeMap.put(model.getNodeNum(), model);
                nodes.postValue(new ArrayList<>(nodeMap.values()));
                if (model.getNodeNum() != 0) {
                    updateDeviceStatus(s -> {
                        s.setSnr(model.getSnr());
                        s.setBatteryPercent(model.getBatteryLevel());
                        s.setLastHeard(model.getLastHeard());
                    });
                }
                break;
            }
            case MY_INFO: {
                statusText.postValue("Подключено: my_num=" + msg.getMyInfo().getMyNodeNum());
                updateDeviceStatus(s -> s.setNodeNum((long) msg.getMyInfo().getMyNodeNum()));
                break;
            }
            case METADATA: {
                updateDeviceStatus(s -> s.setFirmwareVersion(msg.getMetadata().getFirmwareVersion()));
                break;
            }
            default:
                break;
        }
    }

    private void updateDeviceStatus(java.util.function.Consumer<DeviceStatus> updater) {
        DeviceStatus current = deviceStatus.getValue();
        if (current == null) current = new DeviceStatus();
        updater.accept(current);
        deviceStatus.postValue(current);
    }

    private static String toHex(byte[] data) {
        if (data == null || data.length == 0) return "—";
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private void requestConfig() {
        int configId = wantConfigId++;
        MeshProtos.ToRadio msg = MeshProtos.ToRadio.newBuilder()
                .setWantConfigId(configId)
                .build();
        sendToRadio(msg);
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
            // В protobuf latitudeI/longitudeI - int32 в 1e-7 градуса
            if (p.hasLatitudeI()) n.setLatitude(p.getLatitudeI() / 1e7);
            if (p.hasLongitudeI()) n.setLongitude(p.getLongitudeI() / 1e7);
        }

        n.setSnr(ni.getSnr());
        n.setLastHeard(ni.getLastHeard());
        n.setViaMqtt(ni.getViaMqtt());

        if (ni.hasDeviceMetrics() && ni.getDeviceMetrics().hasBatteryLevel()) {
            n.setBatteryLevel(ni.getDeviceMetrics().getBatteryLevel());
        }

        if (ni.hasHopsAway()) n.setHopsAway(ni.getHopsAway());
        if (ni.getChannel() != 0) n.setChannel(ni.getChannel());

        return n;
    }

    /** Builds Meshtastic "length-delimited" frame: [varint32 length][payload]. */
    private static byte[] frameDelimited(byte[] payload) {
        if (payload == null) payload = new byte[0];
        byte[] len = encodeVarint32(payload.length);
        byte[] out = new byte[len.length + payload.length];
        System.arraycopy(len, 0, out, 0, len.length);
        System.arraycopy(payload, 0, out, len.length, payload.length);
        return out;
    }

    /** Protobuf varint32 encoder (little-endian base-128). */
    private static byte[] encodeVarint32(int value) {
        // max 5 bytes for 32-bit
        byte[] tmp = new byte[5];
        int i = 0;
        while ((value & ~0x7F) != 0) {
            tmp[i++] = (byte) ((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        tmp[i++] = (byte) (value & 0x7F);
        byte[] out = new byte[i];
        System.arraycopy(tmp, 0, out, 0, i);
        return out;
    }

}
