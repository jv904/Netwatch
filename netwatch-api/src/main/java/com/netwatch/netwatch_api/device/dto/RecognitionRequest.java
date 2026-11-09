package com.netwatch.netwatch_api.device.dto;

import jakarta.validation.constraints.NotNull;

public record RecognitionRequest(
        @NotNull(message = "Informe se o dispositivo é reconhecido.")
        Boolean recognized) {
}
