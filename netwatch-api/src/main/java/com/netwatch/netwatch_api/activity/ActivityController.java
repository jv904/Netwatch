package com.netwatch.netwatch_api.activity;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netwatch.netwatch_api.activity.ActivityService.EventFilter;
import com.netwatch.netwatch_api.activity.dto.ActivitySummaryResponse;
import com.netwatch.netwatch_api.activity.dto.EventResponse;
import com.netwatch.netwatch_api.common.web.PageResponse;
import com.netwatch.netwatch_api.device.DeviceService;

@RestController
@RequestMapping("/api")
public class ActivityController {

    private final ActivityService activity;
    private final DeviceService devices;

    public ActivityController(ActivityService activity, DeviceService devices) {
        this.activity = activity;
        this.devices = devices;
    }

    /** Histórico de atividades. Ex.: {@code /api/events?window=7d&severity=WARNING&q=192.168.0.10}. */
    @GetMapping("/events")
    public PageResponse<EventResponse> events(
            @RequestParam(required = false) EventType type,
            @RequestParam(required = false) EventCategory category,
            @RequestParam(required = false) EventSeverity severity,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String window,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        EventFilter filter = new EventFilter(type, category, severity, deviceId, q,
                window != null ? ActivityWindow.parse(window) : null);
        return PageResponse.from(activity.search(filter, pageable), e -> e);
    }

    /** Análise do período: totais, linha do tempo, tipos, horários e dispositivos mais ativos (24h, 7d, 30d). */
    @GetMapping("/events/summary")
    public ActivitySummaryResponse summary(@RequestParam(required = false) String window) {
        return activity.summary(ActivityWindow.parse(window));
    }

    @GetMapping("/devices/{deviceId}/events")
    public PageResponse<EventResponse> deviceEvents(@PathVariable UUID deviceId,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        devices.get(deviceId);
        return PageResponse.from(
                activity.search(new EventFilter(null, null, null, deviceId, null, null), pageable), e -> e);
    }
}
