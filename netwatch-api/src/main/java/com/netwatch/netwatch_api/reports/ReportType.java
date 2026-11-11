package com.netwatch.netwatch_api.reports;

/** Tipos de relatório. */
public enum ReportType {

    OVERVIEW("Relatório geral da rede",
            "Resumo do período: dispositivos, descobertas, exposição, alertas, atividades e tráfego."),
    INVENTORY("Inventário de dispositivos",
            "Todos os equipamentos conhecidos, com identificação, situação e quando foram vistos."),
    EXPOSURE("Exposição de portas e serviços",
            "Portas que aceitam conexões em cada dispositivo, serviços mais expostos e serviços de risco."),
    SECURITY("Segurança e alertas",
            "Alertas abertos no período, gravidade, regras que mais dispararam e tratamento dado a eles."),
    TRAFFIC("Tráfego da rede",
            "Volume ou conexões no período, hosts e serviços mais ativos e sessões de observação.");

    private final String title;
    private final String description;

    ReportType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}
