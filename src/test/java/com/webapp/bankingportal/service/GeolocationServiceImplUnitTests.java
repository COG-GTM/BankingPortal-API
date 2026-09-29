package com.webapp.bankingportal.service;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

class GeolocationServiceImplUnitTests {

    private GeolocationServiceImpl geolocationService;

    @BeforeEach
    void setUp() {
        geolocationService = new GeolocationServiceImpl();
        ReflectionTestUtils.setField(geolocationService, "apiUrl", "http://127.0.0.1:1");
        ReflectionTestUtils.setField(geolocationService, "apiKey", "test-key");
    }

    @Test
    void getGeolocation_failsForUnresolvableAddress() {
        var future = geolocationService.getGeolocation("host.invalid");

        assertTrue(future.isCompletedExceptionally());
        var ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(UnknownHostException.class, ex.getCause());
    }

    @Test
    void getGeolocation_failsWhenApiUnreachable() {
        var future = geolocationService.getGeolocation("127.0.0.1");

        assertTrue(future.isCompletedExceptionally());
        var ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(RestClientException.class, ex.getCause());
    }
}
