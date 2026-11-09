package com.netwatch.netwatch_api.common.net;

public enum PortState {
    /** Conexão aceita: existe um serviço escutando. */
    OPEN,
    /** Conexão recusada: o equipamento respondeu, mas não há serviço na porta. */
    CLOSED,
    /** Sem resposta conclusiva (timeout, firewall descartando pacotes...). */
    FILTERED
}
