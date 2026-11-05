package com.javatodev.finance;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ErrorDisclosureTest {
    @Autowired ErrorAttributes errors;

    @Test
    void fallbackErrorsDoNotExposeAccountPathsOrExceptionDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/account/001234567890");
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "account 001234567890");
        var attributes = errors.getErrorAttributes(new ServletWebRequest(request),
            ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE, ErrorAttributeOptions.Include.STACK_TRACE));
        assertFalse(attributes.toString().contains("001234567890"), attributes.toString());
        assertEquals(404, attributes.get("status"));
    }
}
