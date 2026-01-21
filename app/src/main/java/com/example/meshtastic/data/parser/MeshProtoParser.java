package com.example.meshtastic.data.parser;

import android.util.Log;

import org.meshtastic.proto.MeshProtos;

/**
 * Утилита для парсинга FromRadio/ToRadio protobuf сообщений Meshtastic.
 * Для MVP мы просто формируем человекочитаемую строку для экрана статуса.
 */
public class MeshProtoParser {

    private static final String TAG = "MeshProtoParser";

    /**
     * Пытается распарсить входящие байты как FromRadio и вернуть краткое описание.
     */
    public static String parseFromRadioSummary(byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            MeshProtos.FromRadio msg = MeshProtos.FromRadio.parseFrom(data);
            StringBuilder sb = new StringBuilder();
            sb.append("FromRadio id=").append(msg.getId());

            switch (msg.getPayloadVariantCase()) {
                case MY_INFO:
                    // В разных версиях протокола структура MyNodeInfo может отличаться,
                    // поэтому тут выводим только тип, без доступа к вложенным полям.
                    sb.append(" MY_INFO");
                    break;
                case NODE_INFO:
                    sb.append(" NODE_INFO");
                    break;
                case CONFIG:
                    sb.append(" CONFIG update");
                    break;
                case CHANNEL:
                    sb.append(" CHANNEL info");
                    break;
                case PACKET:
                    sb.append(" PACKET on port ")
                      .append(msg.getPacket().getDecoded().getPortnum().name());
                    break;
                case LOG_RECORD:
                    sb.append(" LOG: ").append(msg.getLogRecord().getMessage());
                    break;
                case METADATA:
                    sb.append(" METADATA: ").append(msg.getMetadata().getFirmwareVersion());
                    break;
                case PAYLOADVARIANT_NOT_SET:
                default:
                    sb.append(" (payload=").append(msg.getPayloadVariantCase().name()).append(")");
                    break;
            }
            return sb.toString();
        } catch (Exception e) {
            Log.d(TAG, "Не удалось распарсить FromRadio: " + e.getMessage());
            return null;
        }
    }
}

