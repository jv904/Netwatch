package com.netwatch.netwatch_api.device;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.common.exception.ConflictException;
import com.netwatch.netwatch_api.common.exception.ResourceNotFoundException;
import com.netwatch.netwatch_api.device.dto.CreateDeviceRequest;
import com.netwatch.netwatch_api.device.dto.DeviceSummary;
import com.netwatch.netwatch_api.device.dto.UpdateDeviceRequest;

@Service
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository repository;
    private final Clock clock;

    public DeviceService(DeviceRepository repository, Clock clock) {
        this.repository = repository;
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
        return repository.save(device);
    }

    @Transactional
    public Device update(UUID id, UpdateDeviceRequest request) {
        Device device = get(id);
        device.setCustomName(blankToNull(request.customName()));
        device.setType(request.type());
        device.setRecognized(request.recognized());
        device.setNotes(blankToNull(request.notes()));
        return device;
    }

    @Transactional
    public Device setRecognized(UUID id, boolean recognized) {
        Device device = get(id);
        device.setRecognized(recognized);
        return device;
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(get(id));
    }

    /**
     * Ponto de entrada da coleta automática (descoberta, captura de pacotes...).
     * <ul>
     *   <li>Se o MAC já é conhecido: atualiza IP, hostname, fabricante e "última vez visto".</li>
     *   <li>Se só existe um cadastro manual com o mesmo IP e sem MAC: associa o MAC a ele.</li>
     *   <li>Caso contrário: cria um novo registro como <b>não reconhecido</b>.</li>
     * </ul>
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
        boolean ipChanged = !created && !Objects.equals(device.getIpAddress(), ip);

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

        return new ObservationResult(repository.save(device), created, ipChanged);
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
                count++;
            }
        }
        return count;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
