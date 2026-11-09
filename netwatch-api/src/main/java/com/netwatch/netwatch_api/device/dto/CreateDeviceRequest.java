package com.netwatch.netwatch_api.device.dto;

import com.netwatch.netwatch_api.device.DeviceType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro manual de um equipamento que o usuário já conhece. */
public record CreateDeviceRequest(
        @NotBlank(message = "O endereço IP é obrigatório.")
        @Size(max = 45)
        String ipAddress,

        @Size(max = 17)
        String macAddress,

        @Size(max = 255)
        String hostname,

        @Size(max = 100)
        String customName,

        DeviceType type,

        @Size(max = 1000)
        String notes) {
}
