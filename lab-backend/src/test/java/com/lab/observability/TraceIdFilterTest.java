package com.lab.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdFilterTest {
    @AfterEach void clear() { MDC.clear(); }

    @Test void acceptsSafeCallerIdAndClearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "web-20261025_12");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceIdFilter().doFilter(request, response, (req, res) ->
                assertEquals("web-20261025_12", MDC.get("traceId")));
        assertEquals("web-20261025_12", response.getHeader("X-Trace-Id"));
        assertNull(MDC.get("traceId"));
    }

    @Test void replacesInvalidAndOverlongIds() throws Exception {
        for (String value : new String[]{"bad\r\nInjected: secret", "", String.join("", java.util.Collections.nCopies(65, "a"))}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Trace-Id", value);
            MockHttpServletResponse response = new MockHttpServletResponse();
            new TraceIdFilter().doFilter(request, response, (req, res) -> { });
            assertTrue(response.getHeader("X-Trace-Id").matches("[a-f0-9]{32}"));
        }
    }

    @Test void exceptionStillClearsThreadState() {
        assertThrows(javax.servlet.ServletException.class, () -> new TraceIdFilter().doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
                    assertNotNull(MDC.get("traceId"));
                    throw new javax.servlet.ServletException("test failure");
                }));
        assertNull(MDC.get("traceId"));
    }
}
