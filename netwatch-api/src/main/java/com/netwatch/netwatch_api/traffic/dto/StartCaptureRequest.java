package com.netwatch.netwatch_api.traffic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * @param interfaceName   nome técnico (ou parte da descrição) da interface; vazio = escolha automática
 * @param durationMinutes duração da sessão; vazio = padrão da configuração
 */
public record StartCaptureRequest(
        @Size(max = 255) String interfaceName,
        @Min(value = 1, message = "A duração mínima é 1 minuto.")
        @Max(value = 1440, message = "A duração máxima é 1440 minutos.") Integer durationMinutes) {
}
