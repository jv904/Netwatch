package com.netwatch.netwatch_api.device;

/**
 * Resultado de registrar uma observação.
 *
 * @param created     o equipamento não existia e foi criado agora (candidato a alerta "novo dispositivo")
 * @param ipChanged   o equipamento já existia mas apareceu com outro IP
 */
public record ObservationResult(Device device, boolean created, boolean ipChanged) {
}
