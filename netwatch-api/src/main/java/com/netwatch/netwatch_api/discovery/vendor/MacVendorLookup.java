package com.netwatch.netwatch_api.discovery.vendor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Identifica o fabricante pelo prefixo OUI (3 primeiros bytes) do MAC,
 * usando a lista pública do IEEE embutida em {@code oui/oui.tsv}.
 */
@Component
public class MacVendorLookup {

    public static final String RANDOMIZED = "MAC aleatório (privacidade)";

    private static final Logger log = LoggerFactory.getLogger(MacVendorLookup.class);
    private static final String RESOURCE = "/oui/oui.tsv";

    private volatile Map<String, String> vendors;

    /**
     * @param mac endereço no formato aa:bb:cc:dd:ee:ff
     */
    public Optional<String> lookup(String mac) {
        if (mac == null || mac.length() < 8) {
            return Optional.empty();
        }
        String prefix = mac.substring(0, 8).replace(":", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (prefix.length() != 6) {
            return Optional.empty();
        }
        String vendor = vendors().get(prefix);
        if (vendor != null) {
            return Optional.of(vendor);
        }
        // Bit "localmente administrado": celulares e notebooks modernos usam MAC aleatório por rede.
        int firstOctet = Integer.parseInt(prefix.substring(0, 2), 16);
        if ((firstOctet & 0x02) != 0) {
            return Optional.of(RANDOMIZED);
        }
        return Optional.empty();
    }

    private Map<String, String> vendors() {
        Map<String, String> loaded = vendors;
        if (loaded == null) {
            synchronized (this) {
                loaded = vendors;
                if (loaded == null) {
                    loaded = load();
                    vendors = loaded;
                }
            }
        }
        return loaded;
    }

    private static Map<String, String> load() {
        Map<String, String> map = new HashMap<>(64_000);
        try (InputStream in = MacVendorLookup.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("Base de fabricantes {} não encontrada; fabricantes não serão identificados.", RESOURCE);
                return map;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab == 6 && !line.startsWith("#")) {
                    map.put(line.substring(0, 6), line.substring(7));
                }
            }
        } catch (IOException e) {
            log.warn("Falha ao carregar a base de fabricantes: {}", e.getMessage());
        }
        return map;
    }
}
