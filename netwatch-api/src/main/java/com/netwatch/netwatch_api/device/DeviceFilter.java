package com.netwatch.netwatch_api.device;

public record DeviceFilter(DeviceStatus status, DeviceType type, Boolean recognized, String search) {
}
