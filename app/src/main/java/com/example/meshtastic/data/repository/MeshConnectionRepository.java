package com.example.meshtastic.data.repository;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.meshtastic.R;
import com.example.meshtastic.bluetooth.BleManager;
import com.example.meshtastic.crypto.E2eCipher;
import com.example.meshtastic.crypto.E2eKeyManager;
import com.example.meshtastic.data.model.DeviceStatus;
import com.example.meshtastic.data.model.Message;
import com.example.meshtastic.data.model.NodeInfo;
import com.example.meshtastic.data.parser.MeshProtoParser;

import com.google.protobuf.InvalidProtocolBufferException;

import org.meshtastic.proto.AdminProtos;
import org.meshtastic.proto.ChannelProtos;
import org.meshtastic.proto.MeshProtos;
import org.meshtastic.proto.Portnums;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Единая точка управления BLE соединением с Meshtastic.
 * Хранит состояние и позволяет нескольким экранам наблюдать за ним через LiveData.
 */
public class MeshConnectionRepository {

    private static final String TAG = "MeshConnectionRepo";

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

    private final Context appContext;
    private final BleManager bleManager;

    private final MutableLiveData<State> state = new MutableLiveData<>(State.DISCONNECTED);
    private final MutableLiveData<String> statusText;
    private final MutableLiveData<List<BluetoothDevice>> devices = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<BluetoothDevice> selectedDevice = new MutableLiveData<>(null);

    private final MutableLiveData<byte[]> lastRx = new MutableLiveData<>(null);
    private final MutableLiveData<String> lastFromRadioSummary = new MutableLiveData<>(null);
    private final MutableLiveData<List<NodeInfo>> nodes = new MutableLiveData<>(new ArrayList<>());

    private final MutableLiveData<List<Message>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Message>> e2eMessages = new MutableLiveData<>(new ArrayList<>());
    private final Object messagesLock = new Object();
    private final Object e2eMessagesLock = new Object();

    private void appendMessage(Message message) {
        synchronized (messagesLock) {
            List<Message> current = messages.getValue();
            List<Message> updated = new ArrayList<>(current != null ? current : new ArrayList<>());
            updated.add(message);
            messages.postValue(updated);
        }
    }

    private void appendE2eMessage(Message message) {
        synchronized (e2eMessagesLock) {
            List<Message> current = e2eMessages.getValue();
            List<Message> updated = new ArrayList<>(current != null ? current : new ArrayList<>());
            updated.add(message);
            e2eMessages.postValue(updated);
        }
    }

    private final Map<Long, NodeInfo> nodeMap = new ConcurrentHashMap<>();
    private final Set<String> seenAddresses = ConcurrentHashMap.newKeySet();

    private int wantConfigId = 1;
    private long myNodeNum = 0;

    private final AtomicInteger packetIdGen = new AtomicInteger(new Random().nextInt());
    private E2eKeyManager e2eKeyManager;

    private final MutableLiveData<DeviceStatus> deviceStatus = new MutableLiveData<>(new DeviceStatus());

    private MeshConnectionRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.bleManager = new BleManager(appContext);
        this.statusText = new MutableLiveData<>(appContext.getString(R.string.repo_state_disconnected));
        this.e2eKeyManager = new E2eKeyManager(appContext);
    }

    private String s(int resId, Object... args) {
        return appContext.getString(resId, args);
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

    public LiveData<List<Message>> getMessages() {
        return messages;
    }

    public LiveData<List<Message>> getE2eMessages() {
        return e2eMessages;
    }

    public E2eKeyManager getE2eKeyManager() {
        return e2eKeyManager;
    }

    public boolean isBluetoothEnabled() {
        return bleManager.isBluetoothEnabled();
    }

    public void startScan() {
        state.postValue(State.SCANNING);
        String scanningMsg = s(R.string.repo_state_scanning);
        statusText.postValue(scanningMsg);
        updateDeviceStatus(ds -> {
            ds.setState(State.SCANNING.name());
            ds.setStatusText(scanningMsg);
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
            statusText.postValue(s(R.string.repo_state_scan_stopped));
        }
    }

    public void selectDevice(BluetoothDevice device) {
        selectedDevice.postValue(device);
    }

    public void connect() {
        BluetoothDevice device = selectedDevice.getValue();
        if (device == null) {
            String noDevice = s(R.string.repo_state_no_device);
            state.postValue(State.ERROR);
            statusText.postValue(noDevice);
            updateDeviceStatus(ds -> {
                ds.setState(State.ERROR.name());
                ds.setStatusText(noDevice);
            });
            return;
        }

        String connectingMsg = s(R.string.repo_state_connecting, safeName(device));
        updateDeviceStatus(ds -> {
            ds.setState(State.CONNECTING.name());
            ds.setDeviceName(safeName(device));
            ds.setStatusText(connectingMsg);
        });

        state.postValue(State.CONNECTING);
        statusText.postValue(connectingMsg);

        bleManager.connect(device, new BleManager.ConnectionListener() {
            @Override
            public void onConnected() {
                // GATT-соединение установлено, но сервисы и CCCD ещё не готовы.
                String msg = s(R.string.repo_state_connected, safeName(device));
                statusText.postValue(msg);
                updateDeviceStatus(ds -> {
                    ds.setState(State.CONNECTING.name());
                    ds.setStatusText(msg);
                });
            }

            @Override
            public void onReady() {
                // CCCD активирован — теперь устройство примет ToRadio.
                String msg = s(R.string.repo_state_ready, safeName(device));
                state.postValue(State.CONNECTED);
                statusText.postValue(msg);
                updateDeviceStatus(ds -> {
                    ds.setState(State.CONNECTED.name());
                    ds.setStatusText(msg);
                });
                requestConfig();
            }

            @Override
            public void onDisconnected() {
                String msg = s(R.string.repo_state_disconnected_short);
                state.postValue(State.DISCONNECTED);
                statusText.postValue(msg);
                updateDeviceStatus(ds -> {
                    ds.setState(State.DISCONNECTED.name());
                    ds.setStatusText(msg);
                });
            }

            @Override
            public void onError(String message) {
                String msg = (message != null) ? message : s(R.string.repo_state_error_generic);
                state.postValue(State.ERROR);
                statusText.postValue(msg);
                updateDeviceStatus(ds -> {
                    ds.setState(State.ERROR.name());
                    ds.setStatusText(msg);
                });
            }
        }, data -> {
            lastRx.postValue(data);
            updateDeviceStatus(ds -> {
                ds.setLastRxAt(System.currentTimeMillis());
                ds.setLastRxHex(toHex(data));
            });
            handleFromRadio(data);
        });
    }

    public void disconnect() {
        bleManager.disconnect();
        state.postValue(State.DISCONNECTED);
        statusText.postValue(s(R.string.repo_state_disconnected_short));
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

    public boolean sendTextMessage(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        if (state.getValue() != State.CONNECTED) return false;

        byte[] payload = text.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        MeshProtos.MeshPacket packet = MeshProtos.MeshPacket.newBuilder()
                .setTo(0xFFFFFFFF) // broadcast
                .setDecoded(MeshProtos.Data.newBuilder()
                        .setPortnum(Portnums.PortNum.TEXT_MESSAGE_APP)
                        .setPayload(com.google.protobuf.ByteString.copyFrom(payload))
                        .build())
                .setId(nextPacketId())
                .setHopLimit(3)
                .setWantAck(true)
                .build();

        MeshProtos.ToRadio msg = MeshProtos.ToRadio.newBuilder()
                .setPacket(packet)
                .build();

        boolean sent = sendToRadio(msg);
        if (sent) {
            String senderId = String.format("!%08x", myNodeNum);
            appendMessage(new Message(text.trim(), senderId, true));
        }
        return sent;
    }

    /**
     * Отправить E2E-зашифрованное личное сообщение конкретному узлу.
     * Использует эфемерный ECDH (P-256) + AES-256-GCM, порт PRIVATE_APP (256).
     */
    public boolean sendE2eMessage(long recipientNodeNum, String recipientPubKeyHex, String text) {
        if (state.getValue() != State.CONNECTED) return false;
        if (text == null || text.trim().isEmpty()) return false;
        if (e2eKeyManager == null || !e2eKeyManager.isAvailable()) return false;

        try {
            java.security.PublicKey recipientPubKey = E2eKeyManager.parsePublicKey(recipientPubKeyHex);
            byte[] plaintext = text.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] encrypted = E2eCipher.encrypt(recipientPubKey, plaintext);

            MeshProtos.MeshPacket packet = MeshProtos.MeshPacket.newBuilder()
                    .setTo((int) (recipientNodeNum & 0xFFFFFFFFL))
                    .setDecoded(MeshProtos.Data.newBuilder()
                            .setPortnum(Portnums.PortNum.PRIVATE_APP)
                            .setPayload(com.google.protobuf.ByteString.copyFrom(encrypted))
                            .setWantResponse(false)
                            .build())
                    .setId(nextPacketId())
                    .setHopLimit(3)
                    .setWantAck(true)
                    .build();

            MeshProtos.ToRadio msg = MeshProtos.ToRadio.newBuilder().setPacket(packet).build();
            boolean sent = sendToRadio(msg);

            if (sent) {
                String senderId = String.format("!%08x", myNodeNum);
                appendE2eMessage(new Message(text.trim(), senderId, true));
            }
            return sent;
        } catch (Exception e) {
            Log.e(TAG, "E2E send failed", e);
            statusText.postValue(s(R.string.e2e_send_failed, e.getMessage()));
            return false;
        }
    }

    /**
     * Применить имя канала + PSK на устройство.
     *
     * <p>PSK интерпретируется как hex (16/32 байта = 32/64 hex-символа) либо пустая строка
     * (отключить шифрование). Любой другой формат отвергается, чтобы не разойтись с прошивкой.
     */
    public boolean applyChannelPsk(String channelName, String pskText) {
        if (channelName == null || channelName.trim().isEmpty()) {
            statusText.postValue(s(R.string.repo_psk_empty_channel));
            return false;
        }
        if (state.getValue() != State.CONNECTED) {
            statusText.postValue(s(R.string.repo_psk_no_connection));
            return false;
        }

        byte[] pskBytes;
        try {
            pskBytes = parsePsk(pskText);
        } catch (IllegalArgumentException e) {
            statusText.postValue(s(R.string.repo_psk_invalid, e.getMessage()));
            return false;
        }

        ChannelProtos.ChannelSettings settings = ChannelProtos.ChannelSettings.newBuilder()
                .setName(channelName.trim())
                .setPsk(com.google.protobuf.ByteString.copyFrom(pskBytes))
                .build();

        ChannelProtos.Channel channel = ChannelProtos.Channel.newBuilder()
                .setIndex(0)
                .setRole(ChannelProtos.Channel.Role.PRIMARY)
                .setSettings(settings)
                .build();

        AdminProtos.AdminMessage admin = AdminProtos.AdminMessage.newBuilder()
                .setSetChannel(channel)
                .build();

        int destNum = (myNodeNum == 0) ? 0xFFFFFFFF : (int) myNodeNum;

        MeshProtos.MeshPacket packet = MeshProtos.MeshPacket.newBuilder()
                .setTo(destNum)
                .setDecoded(MeshProtos.Data.newBuilder()
                        .setPortnum(Portnums.PortNum.ADMIN_APP)
                        .setPayload(admin.toByteString())
                        .setWantResponse(true)
                        .build())
                .setId(nextPacketId())
                .setHopLimit(0)
                .setWantAck(true)
                .build();

        MeshProtos.ToRadio msg = MeshProtos.ToRadio.newBuilder()
                .setPacket(packet)
                .build();

        return sendToRadio(msg);
    }

    /** Возвращает следующий уникальный packet id (положительный 31-битный). */
    private int nextPacketId() {
        int id = packetIdGen.incrementAndGet();
        return id == 0 ? packetIdGen.incrementAndGet() & 0x7FFFFFFF : id & 0x7FFFFFFF;
    }

    /**
     * Разобрать PSK: пустая строка → нет шифрования; иначе hex длиной 32 или 64 символа
     * (=> 16 или 32 байта). Прочее — IllegalArgumentException.
     */
    private static byte[] parsePsk(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return new byte[0];
        if (!t.matches("^[0-9a-fA-F]+$")) {
            throw new IllegalArgumentException("ожидается hex (только 0-9 a-f)");
        }
        if (t.length() != 32 && t.length() != 64) {
            throw new IllegalArgumentException("длина hex должна быть 32 (AES-128) или 64 (AES-256)");
        }
        byte[] out = new byte[t.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(t.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
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
        } catch (InvalidProtocolBufferException e) {
            Log.w(TAG, "FromRadio parse failed (битый protobuf, len=" + data.length + ")", e);
            return;
        } catch (Exception e) {
            Log.e(TAG, "FromRadio parse: непредвиденная ошибка", e);
            return;
        }

        String summary = MeshProtoParser.parseFromRadioSummary(data);
        if (summary != null) {
            lastFromRadioSummary.postValue(summary);
            updateDeviceStatus(ds -> ds.setLastSummary(summary));
        }

        switch (msg.getPayloadVariantCase()) {
            case NODE_INFO: {
                MeshProtos.NodeInfo ni = msg.getNodeInfo();
                NodeInfo model = convertNode(ni);
                nodeMap.put(model.getNodeNum(), model);
                nodes.postValue(new ArrayList<>(nodeMap.values()));
                if (model.getNodeNum() != 0) {
                    updateDeviceStatus(ds -> {
                        ds.setSnr(model.getSnr());
                        ds.setBatteryPercent(model.getBatteryLevel());
                        ds.setLastHeard(model.getLastHeard());
                    });
                }
                break;
            }
            case MY_INFO: {
                long unsignedNum = msg.getMyInfo().getMyNodeNum() & 0xFFFFFFFFL;
                myNodeNum = unsignedNum;
                statusText.postValue(s(R.string.repo_state_my_num, unsignedNum));
                updateDeviceStatus(ds -> ds.setNodeNum(unsignedNum));
                break;
            }
            case PACKET: {
                MeshProtos.MeshPacket packet = msg.getPacket();
                if (packet.hasDecoded()) {
                    MeshProtos.Data decoded = packet.getDecoded();
                    if (decoded.getPortnum() == Portnums.PortNum.TEXT_MESSAGE_APP) {
                        String text = decoded.getPayload().toStringUtf8();
                        long fromNum = packet.getFrom() & 0xFFFFFFFFL;
                        String senderId = String.format("!%08x", fromNum);
                        boolean isOwn = (fromNum != 0 && fromNum == myNodeNum);
                        appendMessage(new Message(text, senderId, isOwn));
                    } else if (decoded.getPortnum() == Portnums.PortNum.PRIVATE_APP) {
                        handleE2ePacket(packet, decoded);
                    }
                }
                break;
            }
            case METADATA: {
                updateDeviceStatus(ds -> ds.setFirmwareVersion(msg.getMetadata().getFirmwareVersion()));
                break;
            }
            default:
                break;
        }
    }

    private void handleE2ePacket(MeshProtos.MeshPacket packet, MeshProtos.Data decoded) {
        long fromNum = packet.getFrom() & 0xFFFFFFFFL;
        // Пакет, который мы сами только что отправили — уже добавлен оптимистично.
        if (fromNum != 0 && fromNum == myNodeNum) return;

        String senderId = String.format("!%08x", fromNum);
        String text;
        boolean decryptFailed = false;

        if (e2eKeyManager != null && e2eKeyManager.isAvailable()) {
            try {
                byte[] plaintext = E2eCipher.decrypt(
                        e2eKeyManager.getPrivateKey(),
                        decoded.getPayload().toByteArray());
                text = new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                Log.w(TAG, "E2E decrypt failed from " + senderId + ": " + e.getMessage());
                text = appContext.getString(R.string.e2e_decrypt_failed);
                decryptFailed = true;
            }
        } else {
            text = appContext.getString(R.string.e2e_decrypt_failed);
            decryptFailed = true;
        }

        Message message = new Message(text, senderId, false);
        message.setDecryptFailed(decryptFailed);
        appendE2eMessage(message);
    }

    private void updateDeviceStatus(java.util.function.Consumer<DeviceStatus> updater) {
        // Копируем перед мутацией: иначе LiveData отдаёт ту же ссылку, и observer'ы,
        // сравнивающие через equals(), пропустят обновление.
        DeviceStatus current = deviceStatus.getValue();
        DeviceStatus copy = (current != null) ? new DeviceStatus(current) : new DeviceStatus();
        updater.accept(copy);
        deviceStatus.postValue(copy);
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

    // TODO(serial): frameDelimited()/encodeVarint32() сейчас не вызываются — зарезервированы
    //               для будущего USB-Serial транспорта (там нужен varint length-prefix).
    /** Builds Meshtastic "length-delimited" frame: [varint32 length][payload]. */
    @SuppressWarnings("unused")
    private static byte[] frameDelimited(byte[] payload) {
        if (payload == null) payload = new byte[0];
        byte[] len = encodeVarint32(payload.length);
        byte[] out = new byte[len.length + payload.length];
        System.arraycopy(len, 0, out, 0, len.length);
        System.arraycopy(payload, 0, out, len.length, payload.length);
        return out;
    }

    /** Protobuf varint32 encoder (little-endian base-128). */
    @SuppressWarnings("unused")
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
