package com.lab.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.dto.AssistantRequest;
import org.junit.jupiter.api.Test;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import static org.junit.jupiter.api.Assertions.*;

class AssistantRequestValidationTest {
    private boolean valid(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(mapper.readValue(json, AssistantRequest.class)).isEmpty();
        }
    }

    @Test void oldAndNullHistoryRequestsRemainValid() throws Exception {
        assertTrue(valid("{\"question\":\"怎么签到\"}"));
        assertTrue(valid("{\"question\":\"怎么签到\",\"history\":null}"));
    }

    @Test void incompleteAndBlankTurnsAreRejected() throws Exception {
        for (String turn : new String[]{"null", "{}", "{\"question\":\"q\",\"answer\":\" \"}", "{\"question\":\" \",\"answer\":\"a\"}"}) {
            assertFalse(valid("{\"question\":\"追问\",\"history\":[" + turn + "]}"), turn);
        }
    }

    @Test void historyMustFitThreeBoundedPairs() throws Exception {
        String turn = "{\"question\":\"q\",\"answer\":\"a\"}";
        assertTrue(valid("{\"question\":\"追问\",\"history\":[" + turn + "," + turn + "," + turn + "]}"));
        assertFalse(valid("{\"question\":\"追问\",\"history\":[" + turn + "," + turn + "," + turn + "," + turn + "]}"));
        assertFalse(valid("{\"question\":\"追问\",\"history\":[{\"question\":\"" + repeat(1001) + "\",\"answer\":\"a\"}]}"));
        assertFalse(valid("{\"question\":\"追问\",\"history\":[{\"question\":\"q\",\"answer\":\"" + repeat(3001) + "\"}]}"));
    }
    private String repeat(int n) { return new String(new char[n]).replace('\0', 'x'); }
}
