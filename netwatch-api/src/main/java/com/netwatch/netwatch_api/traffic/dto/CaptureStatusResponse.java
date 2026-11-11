package com.netwatch.netwatch_api.traffic.dto;

import java.util.List;

import com.netwatch.netwatch_api.traffic.CaptureMode;

/**
 * @param enabled                  observação de tráfego ligada na configuração
 * @param available                há alguma fonte de dados utilizável
 * @param mode                     fonte usada na próxima sessão (pacotes ou tabela de conexões)
 * @param packetCaptureAvailable   Npcap/libpcap carregado
 * @param connectionTableAvailable tabela de conexões do sistema disponível (Windows)
 * @param reason                   por que não há fonte, ou a limitação da fonte escolhida
 * @param libraryVersion           versão do Npcap/libpcap
 * @param authorizedNetworks       redes às quais a observação fica restrita
 * @param activeSession            sessão em andamento, se houver
 * @param lastSession              sessão mais recente (em andamento ou não)
 */
public record CaptureStatusResponse(
        boolean enabled,
        boolean available,
        CaptureMode mode,
        boolean packetCaptureAvailable,
        boolean connectionTableAvailable,
        String reason,
        String libraryVersion,
        List<String> authorizedNetworks,
        int maxDurationMinutes,
        int defaultDurationMinutes,
        int retentionDays,
        CaptureSessionResponse activeSession,
        CaptureSessionResponse lastSession) {
}
