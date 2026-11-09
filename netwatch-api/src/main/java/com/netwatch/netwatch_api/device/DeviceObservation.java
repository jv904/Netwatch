package com.netwatch.netwatch_api.device;

/**
 * Um equipamento observado na rede por algum mecanismo de coleta
 * (descoberta ARP/ping, captura de pacotes etc.). Apenas o IP é obrigatório.
 */
public record DeviceObservation(String ipAddress, String macAddress, String hostname, String vendor) {
}
