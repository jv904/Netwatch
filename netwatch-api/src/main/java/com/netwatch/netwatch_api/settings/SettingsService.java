package com.netwatch.netwatch_api.settings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.activity.ActivityEvent;
import com.netwatch.netwatch_api.activity.ActivityProperties;
import com.netwatch.netwatch_api.activity.EventType;
import com.netwatch.netwatch_api.common.exception.InvalidInputException;
import com.netwatch.netwatch_api.discovery.DiscoveryProperties;
import com.netwatch.netwatch_api.discovery.Ipv4Network;
import com.netwatch.netwatch_api.ports.PortScanProperties;
import com.netwatch.netwatch_api.reports.ReportProperties;
import com.netwatch.netwatch_api.traffic.CaptureProperties;

/**
 * Configurações em vigor: o que foi alterado pela tela (banco) ou, se nada foi alterado, o valor do
 * application.yaml/variável de ambiente. Os valores ficam em memória; os módulos consultam a cada uso, então
 * uma alteração vale na hora.
 */
@Service
public class SettingsService {

    private final AppSettingRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Map<SettingKey, String> defaults = new EnumMap<>(SettingKey.class);
    private volatile Map<SettingKey, AppSetting> overrides;

    public SettingsService(AppSettingRepository repository, ApplicationEventPublisher events, Clock clock,
            DiscoveryProperties discovery, PortScanProperties ports, CaptureProperties capture,
            ActivityProperties activity, ReportProperties reports) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
        defaults.put(SettingKey.DISCOVERY_AUTHORIZED_NETWORKS, normalizeCidrs(String.join(",", discovery.authorizedNetworks())));
        defaults.put(SettingKey.DISCOVERY_SCHEDULE_ENABLED, String.valueOf(discovery.schedule().enabled()));
        defaults.put(SettingKey.DISCOVERY_SCHEDULE_INTERVAL,
                String.valueOf(Math.max(5, discovery.schedule().interval().toMinutes())));
        defaults.put(SettingKey.PORTS_TIMEOUT, String.valueOf(ports.timeout().toMillis()));
        defaults.put(SettingKey.CAPTURE_ENABLED, String.valueOf(capture.enabled()));
        defaults.put(SettingKey.CAPTURE_MODE,
                capture.mode() == null ? "auto" : capture.mode().trim().toLowerCase(Locale.ROOT));
        defaults.put(SettingKey.CAPTURE_POLL_SECONDS,
                String.valueOf(Math.max(1, capture.connectionPollInterval().toSeconds())));
        defaults.put(SettingKey.CAPTURE_DEFAULT_DURATION, String.valueOf(capture.defaultDuration().toMinutes()));
        defaults.put(SettingKey.CAPTURE_MAX_DURATION, String.valueOf(capture.maxDuration().toMinutes()));
        defaults.put(SettingKey.CAPTURE_RETENTION_DAYS, String.valueOf(capture.retentionDays()));
        defaults.put(SettingKey.ACTIVITY_RETENTION_DAYS, String.valueOf(activity.retentionDays()));
        defaults.put(SettingKey.REPORTS_RETENTION_DAYS, String.valueOf(reports.retentionDays()));
        defaults.put(SettingKey.REPORTS_WEEKLY, String.valueOf(reports.weekly()));
        defaults.put(SettingKey.REPORTS_MONTHLY, String.valueOf(reports.monthly()));
    }

    // ------------------------------------------------------------------ leitura (usada pelos módulos)

    public String value(SettingKey key) {
        AppSetting override = overrides().get(key);
        return override != null ? override.getValue() : defaults.get(key);
    }

    public boolean bool(SettingKey key) {
        return Boolean.parseBoolean(value(key));
    }

    public int integer(SettingKey key) {
        return Integer.parseInt(value(key));
    }

    public Duration minutes(SettingKey key) {
        return Duration.ofMinutes(integer(key));
    }

    /** Lista de faixas CIDR (vazia quando não configurada). */
    public List<String> cidrs(SettingKey key) {
        String v = value(key);
        return v == null || v.isBlank() ? List.of() : List.of(v.split(","));
    }

    public boolean isCustom(SettingKey key) {
        return overrides().containsKey(key);
    }

    public record SettingView(SettingKey setting, String key, SettingKey.Group group, String groupTitle,
            SettingKey.Type type, String label, String description, String value, String defaultValue, boolean custom,
            Integer min, Integer max, List<String> options, String unit, Instant updatedAt) {
    }

    public List<SettingView> all() {
        Map<SettingKey, AppSetting> current = overrides();
        return Arrays.stream(SettingKey.values()).map(k -> {
            AppSetting o = current.get(k);
            boolean numeric = k.type() == SettingKey.Type.INTEGER;
            return new SettingView(k, k.key(), k.group(), k.group().title(), k.type(), k.label(), k.description(),
                    value(k), defaults.get(k), o != null, numeric ? k.min() : null, numeric ? k.max() : null,
                    k.options(), k.unit(), o != null ? o.getUpdatedAt() : null);
        }).toList();
    }

    // ------------------------------------------------------------------ alteração

    /**
     * Valida e aplica as alterações (tudo ou nada). Um valor igual ao de fábrica remove a personalização.
     *
     * @param changes chave (ex.: {@code capture.mode}) → novo valor
     */
    @Transactional
    public List<SettingView> update(Map<String, String> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new InvalidInputException("Nenhuma configuração informada.");
        }
        Map<SettingKey, String> normalized = new LinkedHashMap<>();
        changes.forEach((rawKey, rawValue) -> {
            SettingKey key = SettingKey.of(rawKey);
            if (key == null) {
                throw new InvalidInputException("Configuração desconhecida: " + rawKey + ".");
            }
            normalized.put(key, normalize(key, rawValue));
        });
        int defaultDuration = Integer.parseInt(normalized.getOrDefault(SettingKey.CAPTURE_DEFAULT_DURATION,
                value(SettingKey.CAPTURE_DEFAULT_DURATION)));
        int maxDuration = Integer.parseInt(normalized.getOrDefault(SettingKey.CAPTURE_MAX_DURATION,
                value(SettingKey.CAPTURE_MAX_DURATION)));
        if (defaultDuration > maxDuration) {
            throw new InvalidInputException("A duração padrão de uma sessão não pode passar da duração máxima ("
                    + maxDuration + " minutos).");
        }

        Instant now = clock.instant();
        List<String> described = new ArrayList<>();
        normalized.forEach((key, newValue) -> {
            String previous = value(key);
            if (newValue.equals(defaults.get(key))) {
                repository.findById(key.key()).ifPresent(repository::delete);
            } else {
                AppSetting setting = repository.findById(key.key()).orElseGet(() -> new AppSetting(key.key(), newValue, now));
                setting.change(newValue, now);
                repository.save(setting);
            }
            if (!newValue.equals(previous)) {
                described.add(key.label() + ": " + display(previous) + " -> " + display(newValue));
            }
        });
        reload();
        if (!described.isEmpty()) {
            events.publishEvent(ActivityEvent.of(EventType.SETTINGS_CHANGED, null, null,
                    described.size() == 1 ? "Configuração alterada: " + described.getFirst()
                            : described.size() + " configurações alteradas")
                    .withDetails(String.join("; ", described)));
        }
        return all();
    }

    /** Volta ao valor do application.yaml. */
    @Transactional
    public List<SettingView> restore(String rawKey) {
        SettingKey key = SettingKey.of(rawKey);
        if (key == null) {
            throw new InvalidInputException("Configuração desconhecida: " + rawKey + ".");
        }
        return update(Map.of(key.key(), defaults.get(key)));
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "(vazio)" : value;
    }

    String normalize(SettingKey key, String raw) {
        String v = raw == null ? "" : raw.trim();
        return switch (key.type()) {
            case BOOLEAN -> {
                if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
                    throw invalid(key, "use true ou false");
                }
                yield v.toLowerCase(Locale.ROOT);
            }
            case INTEGER -> {
                try {
                    int n = Integer.parseInt(v);
                    if (n < key.min() || n > key.max()) {
                        throw invalid(key, "use um valor entre " + key.min() + " e " + key.max());
                    }
                    yield String.valueOf(n);
                } catch (NumberFormatException e) {
                    throw invalid(key, "informe um número");
                }
            }
            case CHOICE -> {
                String lower = v.toLowerCase(Locale.ROOT);
                if (!key.options().contains(lower)) {
                    throw invalid(key, "use " + String.join(", ", key.options()));
                }
                yield lower;
            }
            case CIDR_LIST -> normalizeCidrs(v);
        };
    }

    /** Faixas privadas em notação CIDR, normalizadas e sem repetição. */
    static String normalizeCidrs(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value != null) {
            for (String part : value.split("[,;\\s]+")) {
                if (part.isBlank()) {
                    continue;
                }
                Ipv4Network network = Ipv4Network.parse(part);
                if (!network.isPrivate()) {
                    throw new InvalidInputException("Somente redes privadas podem ser autorizadas: " + network + ".");
                }
                result.add(network.toString());
            }
        }
        return String.join(",", result);
    }

    private static InvalidInputException invalid(SettingKey key, String reason) {
        return new InvalidInputException("\"" + key.label() + "\": " + reason + ".");
    }

    // ------------------------------------------------------------------ cache

    private Map<SettingKey, AppSetting> overrides() {
        Map<SettingKey, AppSetting> current = overrides;
        if (current == null) {
            synchronized (this) {
                if (overrides == null) {
                    reload();
                }
                current = overrides;
            }
        }
        return current;
    }

    /** Relê o banco (chamado depois de cada alteração). */
    public synchronized void reload() {
        Map<SettingKey, AppSetting> loaded = new EnumMap<>(SettingKey.class);
        for (AppSetting s : repository.findAll()) {
            SettingKey key = SettingKey.of(s.getKey());
            if (key != null) {
                loaded.put(key, s);
            }
        }
        overrides = loaded;
    }
}
