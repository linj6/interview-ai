package com.linj6.interviewagent.interview.interfaces.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.linj6.interviewagent.interview.domain.QuestionStatus;

public record QuestionResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long id, String type, QuestionStatus status, String content) {}
