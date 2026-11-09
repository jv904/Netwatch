package com.netwatch.netwatch_api.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

/**
 * Faixa IPv4 em notação CIDR (ex.: {@code 192.168.0.0/24}).
 * O endereço de rede é sempre normalizado ({@code 192.168.0.10/24} vira {@code 192.168.0.0/24}).
 */
public record Ipv4Network(int network, int prefixLength) {

    private static final Pattern CIDR = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})/(\\d{1,2})$");

    public Ipv4Network {
        if (prefixLength < 0 || prefixLength > 32) {
            throw new InvalidInputException("Prefixo de rede inválido: /" + prefixLength);
        }
        network = network & mask(prefixLength);
    }

    public static Ipv4Network parse(String cidr) {
        if (cidr == null) {
            throw new InvalidInputException("Informe a faixa de rede no formato CIDR (ex.: 192.168.0.0/24).");
        }
        Matcher m = CIDR.matcher(cidr.trim());
        if (!m.matches()) {
            throw new InvalidInputException("Faixa de rede inválida: " + cidr + ". Use o formato CIDR (ex.: 192.168.0.0/24).");
        }
        int address = 0;
        for (int i = 1; i <= 4; i++) {
            int octet = Integer.parseInt(m.group(i));
            if (octet > 255) {
                throw new InvalidInputException("Faixa de rede inválida: " + cidr);
            }
            address = (address << 8) | octet;
        }
        int prefix = Integer.parseInt(m.group(5));
        if (prefix > 32) {
            throw new InvalidInputException("Prefixo de rede inválido: /" + prefix);
        }
        return new Ipv4Network(address, prefix);
    }

    public static Ipv4Network of(byte[] address, int prefixLength) {
        return new Ipv4Network(toInt(address), prefixLength);
    }

    /** Converte "a.b.c.d" em inteiro; retorna vazio se não for IPv4. */
    public static OptionalInt toInt(String ip) {
        if (ip == null) {
            return OptionalInt.empty();
        }
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            return OptionalInt.empty();
        }
        int value = 0;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return OptionalInt.empty();
            }
            int octet = Integer.parseInt(part);
            if (octet > 255) {
                return OptionalInt.empty();
            }
            value = (value << 8) | octet;
        }
        return OptionalInt.of(value);
    }

    public static String toString(int address) {
        return ((address >>> 24) & 0xff) + "." + ((address >>> 16) & 0xff) + "."
                + ((address >>> 8) & 0xff) + "." + (address & 0xff);
    }

    public boolean contains(String ip) {
        OptionalInt value = toInt(ip);
        return value.isPresent() && contains(value.getAsInt());
    }

    public boolean contains(int address) {
        return (address & mask(prefixLength)) == network;
    }

    public boolean contains(Ipv4Network other) {
        return other.prefixLength >= prefixLength && contains(other.network);
    }

    /** Endereços privados (RFC 1918): 10/8, 172.16/12, 192.168/16. */
    public boolean isPrivate() {
        return parse("10.0.0.0/8").contains(this)
                || parse("172.16.0.0/12").contains(this)
                || parse("192.168.0.0/16").contains(this);
    }

    /** Quantidade de hosts utilizáveis (exclui rede e broadcast quando o prefixo for menor que /31). */
    public long hostCount() {
        long size = 1L << (32 - prefixLength);
        return prefixLength >= 31 ? size : size - 2;
    }

    /** Lista dos endereços de host da faixa. */
    public List<String> hosts() {
        long size = 1L << (32 - prefixLength);
        long first = prefixLength >= 31 ? 0 : 1;
        long last = prefixLength >= 31 ? size - 1 : size - 2;
        List<String> hosts = new ArrayList<>((int) Math.max(0, last - first + 1));
        for (long i = first; i <= last; i++) {
            hosts.add(toString(network + (int) i));
        }
        return hosts;
    }

    private static int mask(int prefixLength) {
        return prefixLength == 0 ? 0 : -1 << (32 - prefixLength);
    }

    private static int toInt(byte[] address) {
        if (address.length != 4) {
            throw new IllegalArgumentException("Endereço não é IPv4");
        }
        return ((address[0] & 0xff) << 24) | ((address[1] & 0xff) << 16) | ((address[2] & 0xff) << 8) | (address[3] & 0xff);
    }

    @Override
    public String toString() {
        return toString(network) + "/" + prefixLength;
    }
}
