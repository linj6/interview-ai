package com.linj6.interviewagent.interview.interfaces.request;

import jakarta.validation.constraints.NotBlank;

public record AnswerSubmissionRequest(@NotBlank(message = "content 不能为空") String content) {}
