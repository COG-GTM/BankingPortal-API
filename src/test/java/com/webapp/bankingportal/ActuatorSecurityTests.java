package com.webapp.bankingportal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,info,env,heapdump,configprops,mappings,loggers,beans,metrics",
        "management.health.defaults.enabled=false"
})
public class ActuatorSecurityTests extends BaseTest {

    @Test
    public void test_actuator_health_is_public() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("UP"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator",
            "/actuator/env",
            "/actuator/heapdump",
            "/actuator/configprops",
            "/actuator/mappings",
            "/actuator/loggers",
            "/actuator/beans",
            "/actuator/metrics",
            "/actuator/info"
    })
    public void test_other_actuator_endpoints_require_authentication(String path) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(path))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    public void test_actuator_health_prefix_does_not_open_siblings() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/healthz"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

}
