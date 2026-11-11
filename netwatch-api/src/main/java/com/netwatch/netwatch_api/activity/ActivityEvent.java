package com.netwatch.netwatch_api.activity;

import java.util.UUID;

/**
 * Evento publicado pelos módulos (dispositivos, descoberta, portas, tráfego) com o
 * {@link org.springframework.context.ApplicationEventPublisher}. O {@link ActivityRecorder} grava cada um
 * no histórico; as próximas fases (motor de segurança, alertas) também podem escutá-los.
 *
 * @param deviceId    dispositivo envolvido, se houver
 * @param ipAddress   IP envolvido, se houver
 * @param title       frase curta em português ("Novo dispositivo 192.168.0.10 na rede")
 * @param details     informação complementar (opcional)
 * @param referenceId execução de origem (descoberta, verificação de portas, sessão de captura)
 * @param port        porta envolvida (eventos de portas e serviços), usada pelo motor de segurança
 */
public record ActivityEvent(
        EventType type,
        UUID deviceId,
        String ipAddress,
        String title,
        String details,
        UUID referenceId,
        Integer port) {

    public static ActivityEvent of(EventType type, UUID deviceId, String ipAddress, String title) {
        return new ActivityEvent(type, deviceId, ipAddress, title, null, null, null);
    }

    public ActivityEvent withDetails(String value) {
        return new ActivityEvent(type, deviceId, ipAddress, title, value, referenceId, port);
    }

    public ActivityEvent withReference(UUID value) {
        return new ActivityEvent(type, deviceId, ipAddress, title, details, value, port);
    }

    public ActivityEvent withPort(Integer value) {
        return new ActivityEvent(type, deviceId, ipAddress, title, details, referenceId, value);
    }
}
