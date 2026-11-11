package com.netwatch.netwatch_api.activity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param retentionDays dias que os eventos ficam no histórico
 */
@ConfigurationProperties("netwatch.activity")
public record ActivityProperties(@DefaultValue("90") int retentionDays) {
}
