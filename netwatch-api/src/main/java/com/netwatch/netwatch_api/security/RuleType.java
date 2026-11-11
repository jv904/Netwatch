package com.netwatch.netwatch_api.security;

import java.util.List;

import com.netwatch.netwatch_api.security.RuleParam.Type;

/**
 * Catálogo das regras do motor de segurança, com os valores de fábrica. O banco guarda só o que o
 * usuário ajustou (ligada/desligada, gravidade e parâmetros).
 */
public enum RuleType {

    UNKNOWN_DEVICE(Category.DEVICES, AlertSeverity.MEDIUM,
            "Dispositivo desconhecido na rede",
            "Um equipamento que nunca tinha sido visto apareceu na rede e ainda não foi reconhecido.",
            "Identifique o equipamento (fabricante, local, responsável). Se for legítimo, marque-o como conhecido; "
                    + "se não, bloqueie-o no roteador ou switch.",
            List.of()),

    RISKY_SERVICE(Category.DEVICES, AlertSeverity.HIGH,
            "Serviço de risco exposto",
            "Um dispositivo aceita conexões em um serviço frequentemente explorado (Telnet, FTP, SMB, RDP, VNC, "
                    + "bancos de dados, DVR...).",
            "Confirme se o serviço é necessário. Desative-o ou restrinja o acesso por firewall, troque senhas "
                    + "padrão e mantenha o equipamento atualizado.",
            List.of(new RuleParam("ports", "Portas de risco",
                    "Portas consideradas sensíveis, separadas por vírgula.", Type.PORT_LIST,
                    "21,23,139,445,1433,1883,3306,3389,5432,5900,27017,37777"))),

    NEW_OPEN_PORT(Category.DEVICES, AlertSeverity.MEDIUM,
            "Porta nova em dispositivo conhecido",
            "Uma porta que estava fechada passou a aceitar conexões em um equipamento já reconhecido.",
            "Verifique se houve instalação ou atualização de software no equipamento. Um serviço novo sem "
                    + "explicação pode indicar comprometimento.",
            List.of()),

    IP_CONFLICT(Category.NETWORK, AlertSeverity.HIGH,
            "Conflito de IP / possível ARP spoofing",
            "Um IP em uso por um equipamento apareceu com outro endereço MAC pouco tempo depois. Pode ser IP "
                    + "duplicado ou alguém se passando por outro equipamento (fica crítico quando é o roteador).",
            "Compare os dois MACs com o inventário. Se o IP for do roteador ou de um servidor, investigue "
                    + "imediatamente: pode haver interceptação do tráfego (ataque man-in-the-middle).",
            List.of(new RuleParam("criticalIps", "IPs críticos",
                    "Roteador, servidores... Vazio = considera críticos os finais .1 e .254 de cada rede.",
                    Type.IP_LIST, ""))),

    PORT_SCAN(Category.TRAFFIC, AlertSeverity.HIGH,
            "Varredura de portas ou de rede",
            "Um endereço tentou muitas portas de um mesmo equipamento, ou muitos equipamentos da rede, "
                    + "em pouco tempo.",
            "Se a origem for desta rede e não for uma ferramenta autorizada, isole o equipamento e verifique "
                    + "a presença de malware.",
            List.of(new RuleParam("portThreshold", "Portas distintas",
                            "Portas diferentes de um mesmo destino que caracterizam varredura.", Type.INTEGER, "20"),
                    new RuleParam("hostThreshold", "Equipamentos distintos",
                            "Equipamentos diferentes da rede contatados que caracterizam varredura.", Type.INTEGER,
                            "30"),
                    new RuleParam("windowMinutes", "Janela (minutos)",
                            "Período analisado.", Type.INTEGER, "5"))),

    SUSPICIOUS_PORT(Category.TRAFFIC, AlertSeverity.HIGH,
            "Conexão em porta suspeita",
            "Tráfego para portas usadas por backdoors, botnets (IRC), Tor ou acesso remoto inseguro.",
            "Identifique o programa responsável pela conexão (coluna Programa na tela Tráfego) e analise o "
                    + "equipamento com um antivírus.",
            List.of(new RuleParam("ports", "Portas suspeitas", "Portas separadas por vírgula.", Type.PORT_LIST,
                    "23,1337,4444,5555,6666,6667,9001,9050,31337"))),

    EXTERNAL_ADMIN_ACCESS(Category.TRAFFIC, AlertSeverity.HIGH,
            "Acesso administrativo vindo de fora",
            "Um endereço fora das redes autorizadas acessou um serviço administrativo (SSH, RDP, SMB, VNC...) "
                    + "de um equipamento da rede.",
            "Confirme se o acesso remoto é esperado. Caso contrário, feche o redirecionamento de portas no "
                    + "roteador e troque as senhas do equipamento.",
            List.of(new RuleParam("ports", "Portas administrativas", "Portas separadas por vírgula.",
                    Type.PORT_LIST, "22,23,139,445,3389,5900"))),

    OFF_HOURS(Category.SCHEDULE, AlertSeverity.MEDIUM,
            "Atividade fora do horário",
            "Dispositivo novo, porta nova ou serviço novo visto fora do horário de expediente.",
            "Mudanças na rede durante a madrugada são incomuns. Verifique quem estava presente e se havia "
                    + "manutenção programada.",
            List.of(new RuleParam("start", "Início (hora)", "Hora em que começa o período fora do expediente.",
                            Type.HOUR, "22"),
                    new RuleParam("end", "Fim (hora)", "Hora em que termina o período fora do expediente.",
                            Type.HOUR, "6")));

    public enum Category {
        DEVICES, NETWORK, TRAFFIC, SCHEDULE
    }

    private final Category category;
    private final AlertSeverity defaultSeverity;
    private final String title;
    private final String description;
    private final String recommendation;
    private final List<RuleParam> params;

    RuleType(Category category, AlertSeverity defaultSeverity, String title, String description,
            String recommendation, List<RuleParam> params) {
        this.category = category;
        this.defaultSeverity = defaultSeverity;
        this.title = title;
        this.description = description;
        this.recommendation = recommendation;
        this.params = params;
    }

    public Category category() { return category; }
    public AlertSeverity defaultSeverity() { return defaultSeverity; }
    public String title() { return title; }
    public String description() { return description; }
    public String recommendation() { return recommendation; }
    public List<RuleParam> params() { return params; }

    public RuleParam param(String name) {
        return params.stream().filter(p -> p.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Parâmetro desconhecido: " + name));
    }
}
