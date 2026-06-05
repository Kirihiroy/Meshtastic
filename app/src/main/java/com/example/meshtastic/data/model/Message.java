package com.example.meshtastic.data.model;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Модель текстового сообщения в mesh-сети.
 */
public class Message {

    /** Состояние доставки исходящего сообщения. Для входящих не используется. */
    public enum DeliveryStatus {
        /** В процессе отправки (ToRadio записан, но onCharacteristicWrite ещё не пришёл). */
        SENDING,
        /** Отправлено в радиосеть (ToRadio подтверждён прошивкой). */
        SENT,
        /** Доставлено получателю — пришёл Routing ACK. Пока всегда совпадает с SENT
         *  до реализации обработки want_ack/Routing — направление развития. */
        DELIVERED,
        /** Не доставлено за порог времени или write вернул ошибку. */
        FAILED
    }

    private String id;
    private String text;
    private String senderId; // ID узла отправителя
    private long timestamp;
    private boolean isOwnMessage; // true если сообщение отправлено с этого устройства
    private boolean decryptFailed; // true для E2E-пакетов, которые не удалось расшифровать
    private int hopsAway = -1;     // hop_start - hop_limit, для входящих из mesh; -1 = неизвестно
    private DeliveryStatus deliveryStatus = DeliveryStatus.SENT;

    public Message() {
        this.timestamp = System.currentTimeMillis();
        this.id = UUID.randomUUID().toString();
    }

    public Message(String text, String senderId, boolean isOwnMessage) {
        this.text = text;
        this.senderId = senderId;
        this.isOwnMessage = isOwnMessage;
        this.timestamp = System.currentTimeMillis();
        this.id = UUID.randomUUID().toString();
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isOwnMessage() {
        return isOwnMessage;
    }

    public void setOwnMessage(boolean ownMessage) {
        isOwnMessage = ownMessage;
    }

    public boolean isDecryptFailed() {
        return decryptFailed;
    }

    public void setDecryptFailed(boolean decryptFailed) {
        this.decryptFailed = decryptFailed;
    }

    public int getHopsAway() { return hopsAway; }
    public void setHopsAway(int v) { this.hopsAway = v; }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus == null ? DeliveryStatus.SENT : deliveryStatus;
    }
    public void setDeliveryStatus(DeliveryStatus s) {
        this.deliveryStatus = s == null ? DeliveryStatus.SENT : s;
    }

    public String getFormattedTime() {
        return new Date(timestamp).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        Message that = (Message) o;
        return timestamp == that.timestamp
                && isOwnMessage == that.isOwnMessage
                && decryptFailed == that.decryptFailed
                && hopsAway == that.hopsAway
                && Objects.equals(id, that.id)
                && Objects.equals(text, that.text)
                && Objects.equals(senderId, that.senderId)
                && Objects.equals(deliveryStatus, that.deliveryStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
