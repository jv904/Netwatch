package com.netwatch.netwatch_api.device;

/**
 * Um equipamento observado na rede por algum mecanismo de coleta
 * (descoberta ARP/ping, tráfego etc.). Apenas o IP é obrigatório.
 *
 * @param source quem viu o equipamento (aparece no histórico de atividades)
 */
public record DeviceObservation(String ipAddress, String macAddress, String hostname, String vendor,
        Source source) {

    public enum Source {
        DISCOVERY("na descoberta"),
        TRAFFIC("no tráfego");

        private final String description;

        Source(String description) {
            this.description = description;
        }

        /** Complemento de frase: "visto na descoberta". */
        public String description() {
            return description;
        }
    }

    public DeviceObservation {
        if (source == null) {
            source = Source.DISCOVERY;
        }
    }

    public DeviceObservation(String ipAddress, String macAddress, String hostname, String vendor) {
        this(ipAddress, macAddress, hostname, vendor, Source.DISCOVERY);
    }
}
