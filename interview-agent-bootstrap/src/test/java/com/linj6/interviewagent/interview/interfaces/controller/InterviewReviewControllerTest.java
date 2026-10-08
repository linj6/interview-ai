package com.linj6.interviewagent.interview.interfaces.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.application.InterviewApplicationService;
import com.linj6.interviewagent.interview.application.InterviewReviewApplicationService;
import com.linj6.interviewagent.interview.application.InterviewSkipApplicationService;
import com.linj6.interviewagent.interview.application.InterviewTurnApplicationService;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse.Overview;
import com.linj6.interviewagent.web.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InterviewReviewControllerTest {
    private final InterviewApplicationService applicationService = mock(InterviewApplicationService.class);
    private final InterviewTurnApplicationService turnApplicationService = mock(InterviewTurnApplicationService.class);
    private final InterviewSkipApplicationService skipApplicationService = mock(InterviewSkipApplicationService.class);
    private final InterviewReviewApplicationService reviewApplicationService =
            mock(InterviewReviewApplicationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InterviewController(
                        org.mockito.Mockito.mock(
                                com.linj6.interviewagent.interview.application.InterviewPrepareApplicationService
                                        .class),
                        applicationService,
                        turnApplicationService,
                        skipApplicationService,
                        reviewApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsReviewInStandardResultEnvelope() throws Exception {
        LocalDateTime startedAt = LocalDateTime.parse("2026-09-27T10:00:00");
        when(reviewApplicationService.getReview(1L))
                .thenReturn(new InterviewReviewResponse(
                        1L, new Overview(startedAt, startedAt.plusSeconds(60), 60, 1, 0, 1, 0, 0), List.of()));

        mockMvc.perform(get("/api/interviews/1/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.interviewId").value("1"))
                .andExpect(jsonPath("$.data.overview.durationSeconds").value(60))
                .andExpect(jsonPath("$.data.questions").isArray());
    }

    @Test
    void mapsIncompleteInterviewStateConflictToHttpConflict() throws Exception {
        when(reviewApplicationService.getReview(1L))
                .thenThrow(new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT));

        mockMvc.perform(get("/api/interviews/1/review"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(InterviewErrorCode.INTERVIEW_STATE_CONFLICT.getCode()));
    }
}
