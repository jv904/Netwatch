package com.netwatch.netwatch_api.discovery.probe;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Lê a tabela ARP do sistema operacional (somente leitura, sem privilégios). */
@Component
public class SystemArpTable implements ArpTable {

    private static final Logger log = LoggerFactory.getLogger(SystemArpTable.class);
    private static final Path LINUX_ARP = Path.of("/proc/net/arp");

    @Override
    public Map<String, String> read() {
        try {
            if (Files.isReadable(LINUX_ARP)) {
                return ArpTableParser.parse(Files.readString(LINUX_ARP, StandardCharsets.US_ASCII));
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String[] command = os.contains("win") ? new String[] {"arp", "-a"} : new String[] {"arp", "-an"};
            return ArpTableParser.parse(run(command));
        } catch (IOException e) {
            log.warn("Não foi possível ler a tabela ARP: {}", e.getMessage());
            return Map.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        }
    }

    private static String run(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        // Só os trechos ASCII (IP e MAC) importam; o charset do console não afeta a leitura.
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
        return new String(output, Charset.defaultCharset());
    }
}
