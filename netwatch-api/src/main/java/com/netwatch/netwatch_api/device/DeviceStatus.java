package com.netwatch.netwatch_api.device;

public enum DeviceStatus {
    /** Identificado na verificação mais recente. */
    ACTIVE,
    /** Não respondeu recentemente — não significa necessariamente que foi desligado. */
    INACTIVE
}
