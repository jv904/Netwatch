package com.netwatch.netwatch_api.settings;

import java.util.List;

/**
 * Configurações editáveis pela tela. O valor de fábrica vem do application.yaml (ou da variável de ambiente
 * correspondente); o que o usuário altera fica no banco e vale na hora, sem reiniciar.
 */
public enum SettingKey {

    DISCOVERY_AUTHORIZED_NETWORKS("discovery.authorized-networks", Group.DISCOVERY, Type.CIDR_LIST,
            "Redes autorizadas",
            "Faixas privadas que o NetWatch pode verificar e observar, separadas por vírgula (ex.: 192.168.100.0/24). "
                    + "Vazio = sub-redes das placas de rede desta máquina.",
            0, 0, List.of(), null),
    DISCOVERY_SCHEDULE_ENABLED("discovery.schedule.enabled", Group.DISCOVERY, Type.BOOLEAN,
            "Descoberta automática", "Repete a descoberta da rede periodicamente.", 0, 0, List.of(), null),
    DISCOVERY_SCHEDULE_INTERVAL("discovery.schedule.interval-minutes", Group.DISCOVERY, Type.INTEGER,
            "Intervalo da descoberta automática", "Tempo entre o início de uma descoberta automática e a próxima.",
            5, 1440, List.of(), "minutos"),

    PORTS_TIMEOUT("ports.timeout-ms", Group.PORTS, Type.INTEGER,
            "Tempo de espera por porta",
            "Quanto esperar a resposta de cada porta. Valores maiores evitam \"sem resposta\" em redes lentas.",
            200, 10_000, List.of(), "ms"),

    CAPTURE_ENABLED("capture.enabled", Group.TRAFFIC, Type.BOOLEAN,
            "Observação de tráfego", "Permite iniciar sessões na tela Tráfego.", 0, 0, List.of(), null),
    CAPTURE_MODE("capture.mode", Group.TRAFFIC, Type.CHOICE,
            "Fonte do tráfego",
            "Automático usa a captura de pacotes quando o Npcap/libpcap está instalado e, sem ele, a tabela de conexões.",
            0, 0, List.of("auto", "packets", "connections"), null),
    CAPTURE_POLL_SECONDS("capture.connection-poll-seconds", Group.TRAFFIC, Type.INTEGER,
            "Intervalo de leitura das conexões", "Só no modo tabela de conexões.", 1, 60, List.of(), "segundos"),
    CAPTURE_DEFAULT_DURATION("capture.default-duration-minutes", Group.TRAFFIC, Type.INTEGER,
            "Duração padrão de uma sessão", "Usada quando a sessão é iniciada sem escolher a duração.",
            1, 1440, List.of(), "minutos"),
    CAPTURE_MAX_DURATION("capture.max-duration-minutes", Group.TRAFFIC, Type.INTEGER,
            "Duração máxima de uma sessão", "Limite para não deixar uma captura esquecida rodando.",
            1, 1440, List.of(), "minutos"),
    CAPTURE_RETENTION_DAYS("capture.retention-days", Group.RETENTION, Type.INTEGER,
            "Guardar tráfego por", "Fluxos e totais por minuto mais antigos são apagados.", 1, 365, List.of(), "dias"),

    ACTIVITY_RETENTION_DAYS("activity.retention-days", Group.RETENTION, Type.INTEGER,
            "Guardar histórico de atividades por", "Eventos mais antigos são apagados.", 7, 730, List.of(), "dias"),
    REPORTS_RETENTION_DAYS("reports.retention-days", Group.RETENTION, Type.INTEGER,
            "Guardar relatórios por", "Relatórios emitidos há mais tempo são apagados.", 30, 3650, List.of(), "dias"),

    REPORTS_WEEKLY("reports.weekly", Group.REPORTS, Type.BOOLEAN,
            "Relatório semanal automático", "Relatório geral da semana anterior, toda segunda às 6h.",
            0, 0, List.of(), null),
    REPORTS_MONTHLY("reports.monthly", Group.REPORTS, Type.BOOLEAN,
            "Relatório mensal automático", "Relatório geral do mês anterior, todo dia 1º às 6h30.",
            0, 0, List.of(), null);

    public enum Group {
        DISCOVERY("Descoberta e escopo"),
        PORTS("Verificação de portas"),
        TRAFFIC("Tráfego"),
        REPORTS("Relatórios automáticos"),
        RETENTION("Retenção de dados");

        private final String title;

        Group(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    public enum Type {
        BOOLEAN, INTEGER, CHOICE, CIDR_LIST
    }

    private final String key;
    private final Group group;
    private final Type type;
    private final String label;
    private final String description;
    private final int min;
    private final int max;
    private final List<String> options;
    private final String unit;

    SettingKey(String key, Group group, Type type, String label, String description, int min, int max,
            List<String> options, String unit) {
        this.key = key;
        this.group = group;
        this.type = type;
        this.label = label;
        this.description = description;
        this.min = min;
        this.max = max;
        this.options = options;
        this.unit = unit;
    }

    public static SettingKey of(String key) {
        for (SettingKey k : values()) {
            if (k.key.equals(key)) {
                return k;
            }
        }
        return null;
    }

    public String key() { return key; }
    public Group group() { return group; }
    public Type type() { return type; }
    public String label() { return label; }
    public String description() { return description; }
    public int min() { return min; }
    public int max() { return max; }
    public List<String> options() { return options; }
    public String unit() { return unit; }
}
