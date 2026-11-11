package com.netwatch.netwatch_api.security;

import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.netwatch.netwatch_api.activity.ActivityEvent;

/**
 * Resolve sozinho os alertas cujo problema deixou de existir:
 * <ul>
 *   <li>dispositivo marcado como conhecido → "Dispositivo desconhecido";</li>
 *   <li>porta deixou de aceitar conexões → "Serviço de risco" e "Porta nova" daquela porta;</li>
 *   <li>dispositivo removido do inventário → todos os alertas ativos dele.</li>
 * </ul>
 * Roda na hora do evento, em transação própria (a remoção de um dispositivo ainda não aconteceu nesse momento,
 * então os alertas ainda estão ligados a ele). Uma falha aqui nunca desfaz a operação original.
 */
@Component
class AlertAutoResolver {

    private static final Logger log = LoggerFactory.getLogger(AlertAutoResolver.class);

    private final AlertService alerts;
    private final TransactionTemplate newTransaction;

    AlertAutoResolver(AlertService alerts, PlatformTransactionManager transactionManager) {
        this.alerts = alerts;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @EventListener
    void on(ActivityEvent event) {
        switch (event.type()) {
            case DEVICE_RECOGNIZED -> resolve(event, EnumSet.of(RuleType.UNKNOWN_DEVICE), null,
                    "Resolvido automaticamente: o dispositivo foi marcado como conhecido.");
            case PORT_CLOSED -> resolve(event, EnumSet.of(RuleType.RISKY_SERVICE, RuleType.NEW_OPEN_PORT),
                    event.port(), "Resolvido automaticamente: a porta " + event.port()
                            + " deixou de aceitar conexões.");
            case DEVICE_DELETED -> resolve(event, EnumSet.allOf(RuleType.class), null,
                    "Resolvido automaticamente: o dispositivo foi removido do inventário.");
            default -> {
                // Os demais eventos não fecham alertas.
            }
        }
    }

    private void resolve(ActivityEvent event, Set<RuleType> rules, Integer port, String note) {
        if (event.deviceId() == null || (port == null && event.type() == com.netwatch.netwatch_api.activity.EventType.PORT_CLOSED)) {
            return;
        }
        try {
            Integer count = newTransaction.execute(status -> alerts.autoResolve(event.deviceId(), rules, port, note));
            if (count != null && count > 0) {
                log.info("{} alerta(s) resolvido(s) automaticamente após {}.", count, event.type());
            }
        } catch (RuntimeException e) {
            log.warn("Não foi possível resolver alertas automaticamente: {}", e.getMessage());
        }
    }
}
