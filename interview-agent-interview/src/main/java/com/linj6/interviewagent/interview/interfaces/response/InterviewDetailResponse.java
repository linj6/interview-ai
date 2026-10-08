package com.linj6.interviewagent.interview.interfaces.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.linj6.interviewagent.interview.domain.InterviewMode;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.KnowledgeArea;
import java.time.LocalDateTime;

public record InterviewDetailResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
        InterviewStatus status,
        InterviewPhase currentPhase,
        QuestionResponse firstQuestion,
        QuestionResponse currentQuestion,
        LocalDateTime expiresAt,
        InterviewMode mode,
        KnowledgeArea focusArea,
        Boolean planPrepared) {}
