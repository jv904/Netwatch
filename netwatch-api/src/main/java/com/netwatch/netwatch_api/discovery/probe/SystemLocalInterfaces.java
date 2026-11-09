package com.netwatch.netwatch_api.discovery.probe;

import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SystemLocalInterfaces implements LocalInterfaces {

    private static final Logger log = LoggerFactory.getLogger(SystemLocalInterfaces.class);

    private static final Pattern VIRTUAL_NAMES = Pattern.compile(
            "vethernet|wsl|docker|hyper-v|vmware|virtualbox|vbox|loopback|tap-|tun|wireguard|vpn|zerotier|tailscale|hamachi|br-|veth|virbr",
            Pattern.CASE_INSENSITIVE);

    @Override
    public List<LocalAddress> list() {
        List<LocalAddress> result = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                String mac = formatMac(nic.getHardwareAddress());
                String display = nic.getDisplayName() != null ? nic.getDisplayName() : nic.getName();
                boolean virtual = nic.isVirtual()
                        || VIRTUAL_NAMES.matcher(nic.getName()).find()
                        || VIRTUAL_NAMES.matcher(display).find();
                for (InterfaceAddress address : nic.getInterfaceAddresses()) {
                    if (address.getAddress() instanceof Inet4Address ipv4) {
                        result.add(new LocalAddress(nic.getName(), display, ipv4.getHostAddress(),
                                address.getNetworkPrefixLength(), mac, virtual));
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("Não foi possível listar as interfaces de rede: {}", e.getMessage());
        }
        return result;
    }

    private static String formatMac(byte[] hardwareAddress) {
        if (hardwareAddress == null || hardwareAddress.length != 6) {
            return null;
        }
        return HexFormat.ofDelimiter(":").formatHex(hardwareAddress).toLowerCase(Locale.ROOT);
    }
}
