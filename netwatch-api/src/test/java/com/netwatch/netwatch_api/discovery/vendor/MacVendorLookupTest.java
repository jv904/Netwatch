package com.netwatch.netwatch_api.discovery.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MacVendorLookupTest {

    private final MacVendorLookup lookup = new MacVendorLookup();

    @Test
    void findsKnownVendor() {
        assertThat(lookup.lookup("3c:5a:b4:00:00:01")).contains("Google, Inc.");
    }

    @Test
    void flagsRandomizedMac() {
        // 0x02 no primeiro octeto = endereço localmente administrado
        assertThat(lookup.lookup("da:a1:19:00:00:01")).contains(MacVendorLookup.RANDOMIZED);
    }

    @Test
    void returnsEmptyForUnknownOrMissing() {
        assertThat(lookup.lookup(null)).isEmpty();
        assertThat(lookup.lookup("bad")).isEmpty();
    }
}
