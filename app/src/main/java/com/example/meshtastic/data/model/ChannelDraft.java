package com.example.meshtastic.data.model;

/**
 * Локальный черновик одного канала в LoRa-сети.
 * Индекс 0 — всегда PRIMARY (главный канал), индексы 1..7 — SECONDARY/DISABLED.
 */
public class ChannelDraft {

    /** Роли каналов — соответствуют ChannelProtos.Channel.Role. */
    public static final String ROLE_PRIMARY = "PRIMARY";
    public static final String ROLE_SECONDARY = "SECONDARY";
    public static final String ROLE_DISABLED = "DISABLED";

    private int index;          // 0..7
    private String name = "";   // Имя канала; для index=0 пустое = "LongFast" по умолчанию
    private String psk = "";    // hex 32/64 символа или пусто (без шифрования)
    private String role = ROLE_DISABLED;

    public ChannelDraft() {}

    public ChannelDraft(int index) {
        this.index = index;
        this.role = (index == 0) ? ROLE_PRIMARY : ROLE_DISABLED;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name == null ? "" : name; }

    public String getPsk() { return psk == null ? "" : psk; }
    public void setPsk(String psk) { this.psk = psk == null ? "" : psk; }

    public String getRole() { return role == null ? ROLE_DISABLED : role; }
    public void setRole(String role) { this.role = role == null ? ROLE_DISABLED : role; }

    public boolean hasPsk() {
        return getPsk().length() >= 32;
    }
}
