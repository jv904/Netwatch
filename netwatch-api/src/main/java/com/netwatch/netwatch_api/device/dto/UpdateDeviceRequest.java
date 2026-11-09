package com.netwatch.netwatch_api.device.dto;

import com.netwatch.netwatch_api.device.DeviceType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Atualização dos dados que o usuário controla.
 * IP, MAC, hostname e datas de identificação são mantidos pela coleta automática.
 */
public record UpdateDeviceRequest(
        @Size(max = 100)
        String customName,

        @NotNull(message = "O tipo do dispositivo é obrigatório.")
        DeviceType type,

        @NotNull(message = "Informe se o dispositivo é reconhecido.")
        Boolean recognized,

        @Size(max = 1000)
        String notes) {
}
