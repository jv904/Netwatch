package com.netwatch.netwatch_api.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.device.DeviceObservation;
import com.netwatch.netwatch_api.device.DeviceService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DeviceService deviceService;

    @Test
    void overviewAggregatesDeviceData() throws Exception {
        deviceService.registerObservation(new DeviceObservation("192.168.0.1", "aa:00:00:00:00:01", null, "Intelbras"));
        deviceService.registerObservation(new DeviceObservation("192.168.0.2", "aa:00:00:00:00:02", null, "Intelbras"));
        deviceService.registerObservation(new DeviceObservation("192.168.0.3", "aa:00:00:00:00:03", null, null));

        mvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices.total").value(3))
                .andExpect(jsonPath("$.devices.unrecognized").value(3))
                .andExpect(jsonPath("$.openPorts").value(0))
                .andExpect(jsonPath("$.topVendors[0].vendor").value("Intelbras"))
                .andExpect(jsonPath("$.topVendors[0].devices").value(2))
                .andExpect(jsonPath("$.discoveryHistory").isArray());
    }
}
