package com.example.meshtastic.data.parser;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Парсер length-delimited protobuf сообщений (varint длина + payload).
 * Используется для BLE потока Meshtastic, где сообщения могут приходить порциями.
 */
public class ProtobufStreamParser {
    private static final int MAX_VARINT_BYTES = 5; // uint32

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public synchronized List<byte[]> append(byte[] data) {
        if (data == null || data.length == 0) {
            return new ArrayList<>();
        }
        buffer.write(data, 0, data.length);
        return drainFrames();
    }

    private List<byte[]> drainFrames() {
        List<byte[]> frames = new ArrayList<>();
        byte[] bytes = buffer.toByteArray();
        int offset = 0;

        while (offset < bytes.length) {
            VarintResult lengthResult = readVarint32(bytes, offset);
            if (lengthResult == null) {
                break;
            }
            int length = lengthResult.value;
            int header = lengthResult.bytes;
            int start = offset + header;
            int end = start + length;
            if (length < 0 || end > bytes.length) {
                break;
            }
            frames.add(Arrays.copyOfRange(bytes, start, end));
            offset = end;
        }

        if (offset > 0) {
            buffer.reset();
            buffer.write(bytes, offset, bytes.length - offset);
        }
        return frames;
    }

    private static VarintResult readVarint32(byte[] bytes, int offset) {
        int result = 0;
        int shift = 0;
        for (int i = 0; i < MAX_VARINT_BYTES; i++) {
            int index = offset + i;
            if (index >= bytes.length) {
                return null;
            }
            int b = bytes[index] & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return new VarintResult(result, i + 1);
            }
            shift += 7;
        }
        return null;
    }

    private static class VarintResult {
        private final int value;
        private final int bytes;

        private VarintResult(int value, int bytes) {
            this.value = value;
            this.bytes = bytes;
        }
    }
}
