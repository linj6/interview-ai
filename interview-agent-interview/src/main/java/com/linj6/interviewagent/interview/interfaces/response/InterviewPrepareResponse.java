package com.linj6.interviewagent.interview.interfaces.response;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Prepare 仅返回正式持久化标识。 */
public record InterviewPrepareResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long interviewId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long planId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long initialPlanItemId) {}
