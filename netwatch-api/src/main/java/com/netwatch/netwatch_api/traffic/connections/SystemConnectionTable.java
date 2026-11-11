package com.netwatch.netwatch_api.traffic.connections;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lê as conexões com {@code netstat -ano} e os nomes dos processos com {@code tasklist}
 * (Windows; somente leitura, sem privilégios de administrador).
 */
@Component
public class SystemConnectionTable implements ConnectionTable {

    private static final Logger log = LoggerFactory.getLogger(SystemConnectionTable.class);
    private static final Duration PROCESS_CACHE = Duration.ofSeconds(60);

    private final Clock clock;
    private volatile Map<Integer, String> processes = Map.of();
    private volatile Instant processesReadAt = Instant.EPOCH;

    public SystemConnectionTable(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean available() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public List<ConnectionEntry> snapshot() {
        if (!available()) {
            return List.of();
        }
        try {
            List<ConnectionEntry> entries = NetstatParser.parse(run("netstat", "-ano"));
            Map<Integer, String> names = processNames();
            return entries.stream()
                    .map(e -> e.pid() != null ? e.withProcessName(names.get(e.pid())) : e)
                    .toList();
        } catch (IOException e) {
            log.warn("Não foi possível ler a tabela de conexões: {}", e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private Map<Integer, String> processNames() throws IOException, InterruptedException {
        Instant now = clock.instant();
        if (Duration.between(processesReadAt, now).compareTo(PROCESS_CACHE) > 0) {
            processes = TasklistParser.parse(run("tasklist", "/fo", "csv", "/nh"));
            processesReadAt = now;
        }
        return processes;
    }

    private static String run(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
        return new String(output, Charset.defaultCharset());
    }
}
