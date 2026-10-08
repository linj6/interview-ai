package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linj6.interviewagent.interview.domain.*;
import com.linj6.interviewagent.interview.interfaces.request.InterviewCreateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InterviewCreateRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsConfirmedEnumNamesAndPreservesMissingFieldsForValidation() throws Exception {
        for (InterviewMode mode : InterviewMode.values()) {
            assertThat(mapper.readValue("{\"mode\":\"" + mode.name() + "\"}", InterviewCreateRequest.class)
                            .mode())
                    .isEqualTo(mode);
        }
        for (KnowledgeArea area : KnowledgeArea.values()) {
            var request = mapper.readValue(
                    "{\"mode\":\"TECHNICAL_FOCUS\",\"focusArea\":\"" + area.name() + "\"}",
                    InterviewCreateRequest.class);
            assertThat(request.focusArea()).isEqualTo(area);
        }
        assertThat(mapper.readValue("{}", InterviewCreateRequest.class).mode()).isNull();
        assertThat(mapper.readValue("{\"mode\":null,\"focusArea\":null}", InterviewCreateRequest.class)
                        .focusArea())
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"mode\":0}",
                "{\"mode\":\"0\"}",
                "{\"mode\":\"TECHNICAL_FOCUS\",\"focusArea\":0}",
                "{\"mode\":\"comprehensive\"}",
                "{\"mode\":\" COMPREHENSIVE\"}",
                "{\"mode\":\"UNKNOWN\"}",
                "{\"mode\":\"\"}",
                "{\"mode\":\"TECHNICAL_FOCUS\",\"focusArea\":\"UNKNOWN\"}"
            })
    void rejectsValuesOutsideEnumNames(String json) {
        assertThatThrownBy(() -> mapper.readValue(json, InterviewCreateRequest.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
}
