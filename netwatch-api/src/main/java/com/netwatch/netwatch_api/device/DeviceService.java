package com.netwatch.netwatch_api.device;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.activity.ActivityEvent;
import com.netwatch.netwatch_api.activity.EventType;
import com.netwatch.netwatch_api.common.exception.ConflictException;
import com.netwatch.netwatch_api.common.exception.ResourceNotFoundException;
import com.netwatch.netwatch_api.device.dto.CreateDeviceRequest;
import com.netwatch.netwatch_api.device.dto.DeviceSummary;
import com.netwatch.netwatch_api.device.dto.UpdateDeviceRequest;

@Service
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DeviceService(DeviceRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    public Page<Device> search(DeviceFilter filter, Pageable pageable) {
        return repository.findAll(DeviceSpecifications.matching(filter), pageable);
    }

    public Device get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispositivo não encontrado: " + id));
    }

    public DeviceSummary summary() {
        long total = repository.count();
        long active = repository.countByStatus(DeviceStatus.ACTIVE);
        return new DeviceSummary(total, active, total - active, repository.countByRecognizedFalse());
    }

    /**
     * Cadastro manual. Como é o próprio usuário cadastrando, o equipamento já nasce reconhecido.
     * Ele permanece INACTIVE até ser efetivamente visto na rede.
     */
    @Transactional
    public Device create(CreateDeviceRequest request) {
        String ip = NetworkAddresses.normalizeIp(request.ipAddress());
        String mac = NetworkAddresses.normalizeMac(request.macAddress());

        if (mac != null && repository.existsByMacAddress(mac)) {
            throw new ConflictException("Já existe um dispositivo com o MAC " + mac + ".");
        }
        if (mac == null && repository.existsByIpAddressAndMacAddressIsNull(ip)) {
            throw new ConflictException("Já existe um dispositivo sem MAC cadastrado com o IP " + ip + ".");
        }

        Device device = new Device(ip);
        device.setMacAddress(mac);
        device.setHostname(blankToNull(request.hostname()));
        device.setCustomName(blankToNull(request.customName()));
        device.setType(request.type() != null ? request.type() : DeviceType.UNKNOWN);
        device.setNotes(blankToNull(request.notes()));
        device.setRecognized(true);
        Device saved = repository.save(device);
        publish(EventType.DEVICE_CREATED, saved, "Dispositivo " + label(saved) + " cadastrado manualmente");
        return saved;
    }

    @Transactional
    public Device update(UUID id, UpdateDeviceRequest request) {
        Device device = get(id);
        boolean wasRecognized = device.isRecognized();
        device.setCustomName(blankToNull(request.customName()));
        device.setType(request.type());
        device.setRecognized(request.recognized());
        device.setNotes(blankToNull(request.notes()));
        publishRecognitionChange(device, wasRecognized);
        return device;
    }

    @Transactional
    public Device setRecognized(UUID id, boolean recognized) {
        Device device = get(id);
        boolean wasRecognized = device.isRecognized();
        device.setRecognized(recognized);
        publishRecognitionChange(device, wasRecognized);
        return device;
    }

    @Transactional
    public void delete(UUID id) {
        Device device = get(id);
        // Gravado antes da exclusão; o banco desliga o evento do dispositivo (ON DELETE SET NULL).
        publish(EventType.DEVICE_DELETED, device, "Dispositivo " + label(device) + " removido do inventário");
        repository.delete(device);
    }

    /**
     * Ponto de entrada da coleta automática (descoberta, tráfego...).
     * <ul>
     *   <li>Se o MAC já é conhecido: atualiza IP, hostname, fabricante e "última vez visto".</li>
     *   <li>Se só existe um cadastro manual com o mesmo IP e sem MAC: associa o MAC a ele.</li>
     *   <li>Caso contrário: cria um novo registro como <b>não reconhecido</b>.</li>
     * </ul>
     * Novidades (equipamento novo, IP alterado, retorno após inatividade) viram eventos no histórico.
     */
    @Transactional
    public ObservationResult registerObservation(DeviceObservation observation) {
        String ip = NetworkAddresses.normalizeIp(observation.ipAddress());
        String mac = NetworkAddresses.normalizeMac(observation.macAddress());
        Instant now = clock.instant();

        Optional<Device> existing = mac != null
                ? repository.findByMacAddress(mac)
                        .or(() -> repository.findFirstByIpAddressAndMacAddressIsNullOrderByLastSeenAtDesc(ip))
                : repository.findFirstByIpAddressAndMacAddressIsNullOrderByLastSeenAtDesc(ip);

        boolean created = existing.isEmpty();
        Device device = existing.orElseGet(() -> new Device(ip));
        String previousIp = device.getIpAddress();
        boolean ipChanged = !created && !Objects.equals(previousIp, ip);
        boolean returned = !created && device.getStatus() == DeviceStatus.INACTIVE && device.getLastSeenAt() != null;

        device.setIpAddress(ip);
        if (mac != null) {
            device.setMacAddress(mac);
        }
        if (observation.hostname() != null && !observation.hostname().isBlank()) {
            device.setHostname(observation.hostname().trim());
        }
        if (observation.vendor() != null && !observation.vendor().isBlank()) {
            device.setVendor(observation.vendor().trim());
        }
        device.markSeen(now);
        Device saved = repository.save(device);

        String where = observation.source().description();
        if (created) {
            publish(EventType.DEVICE_DISCOVERED, saved, "Novo dispositivo " + label(saved) + " visto " + where,
                    describe(saved));
        } else {
            if (ipChanged) {
                publish(EventType.DEVICE_IP_CHANGED, saved, label(saved) + " mudou de IP: " + previousIp + " → " + ip,
                        null);
            }
            if (returned) {
                publish(EventType.DEVICE_RETURNED, saved, label(saved) + " voltou a ser visto " + where, null);
            }
        }
        return new ObservationResult(saved, created, ipChanged);
    }

    /**
     * Marca como INACTIVE os equipamentos ativos que não foram vistos desde {@code cutoff}
     * e cujo IP pertence ao escopo verificado (ex.: a faixa varrida pela descoberta).
     */
    @Transactional
    public int markInactiveNotSeenSince(Instant cutoff, Predicate<String> ipInScope) {
        int count = 0;
        for (Device device : repository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("status"), DeviceStatus.ACTIVE),
                        cb.lessThan(root.get("lastSeenAt"), cutoff)))) {
            if (ipInScope.test(device.getIpAddress())) {
                device.setStatus(DeviceStatus.INACTIVE);
                publish(EventType.DEVICE_INACTIVE, device, label(device) + " não respondeu na descoberta");
                count++;
            }
        }
        return count;
    }

    private void publishRecognitionChange(Device device, boolean wasRecognized) {
        if (device.isRecognized() == wasRecognized) {
            return;
        }
        if (device.isRecognized()) {
            publish(EventType.DEVICE_RECOGNIZED, device, label(device) + " marcado como conhecido");
        } else {
            publish(EventType.DEVICE_UNRECOGNIZED, device, label(device) + " marcado como desconhecido");
        }
    }

    private void publish(EventType type, Device device, String title) {
        publish(type, device, title, null);
    }

    private void publish(EventType type, Device device, String title, String details) {
        events.publishEvent(ActivityEvent.of(type, device.getId(), device.getIpAddress(), title).withDetails(details));
    }

    /** "Impressora RH (192.168.0.20)" ou só o IP quando não há nome. */
    static String label(Device device) {
        String name = device.getCustomName() != null && !device.getCustomName().isBlank() ? device.getCustomName()
                : device.getHostname();
        return name != null && !name.isBlank() ? name + " (" + device.getIpAddress() + ")" : device.getIpAddress();
    }

    private static String describe(Device device) {
        StringBuilder sb = new StringBuilder();
        if (device.getMacAddress() != null) {
            sb.append("MAC ").append(device.getMacAddress());
        }
        if (device.getVendor() != null) {
            sb.append(sb.isEmpty() ? "" : " · ").append(device.getVendor());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
