package com.netwatch.netwatch_api.ports.dto;

import com.netwatch.netwatch_api.ports.PortScanType;

import jakarta.validation.constraints.Size;

/**
 * @param type  COMMON (padrão) ou CUSTOM
 * @param ports para CUSTOM: lista como "22,80,8000-8100"
 */
public record StartPortScanRequest(PortScanType type, @Size(max = 2000) String ports) {
}
