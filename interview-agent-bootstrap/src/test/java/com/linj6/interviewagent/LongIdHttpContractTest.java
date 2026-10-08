package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linj6.interviewagent.interview.application.*;
import com.linj6.interviewagent.interview.domain.*;
import com.linj6.interviewagent.interview.interfaces.controller.InterviewController;
import com.linj6.interviewagent.interview.interfaces.response.*;
import com.linj6.interviewagent.interview.interfaces.response.AnswerSubmissionResponse.*;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse.*;
import com.linj6.interviewagent.profile.*;
import com.linj6.interviewagent.web.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Real MVC/Jackson boundary with mocked applications; no database or LLM required. */
class LongIdHttpContractTest {
    private static final long ID = 1975511234567890123L;
    private static final String DECIMAL = "1975511234567890123";
    private final InterviewApplicationService interviews = mock(InterviewApplicationService.class);
    private final InterviewPrepareApplicationService prepare = mock(InterviewPrepareApplicationService.class);
    private final InterviewTurnApplicationService turns = mock(InterviewTurnApplicationService.class);
    private final InterviewSkipApplicationService skips = mock(InterviewSkipApplicationService.class);
    private final InterviewReviewApplicationService reviews = mock(InterviewReviewApplicationService.class);
    private final InterviewProfileApplicationService profiles = mock(InterviewProfileApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new InterviewController(prepare, interviews, turns, skips, reviews),
                        new InterviewProfileController(profiles))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createSessionPrepareAnswerAndSkipPreserveLongIdsAtMvcBoundary() throws Exception {
        var question = new QuestionResponse(ID + 1, "MAIN", QuestionStatus.WAITING_ANSWER, "问题");
        var detail = new InterviewDetailResponse(
                ID,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.WAITING_ANSWER,
                question,
                question,
                null,
                InterviewMode.COMPREHENSIVE,
                null,
                true);
        when(interviews.createInterview(InterviewMode.COMPREHENSIVE, null)).thenReturn(detail);
        when(interviews.getInterview(ID)).thenReturn(detail);
        when(prepare.prepareInterview(ID)).thenReturn(new InterviewPrepareResponse(ID, ID + 2, ID + 3));
        when(skips.skipQuestion(ID, ID + 1)).thenReturn(detail);
        var submission = new AnswerSubmissionResponse(
                new AnswerResponse(ID + 4, "回答"),
                new EvaluationResponse(
                        ID + 5,
                        ID + 6,
                        BigDecimal.valueOf(8),
                        BigDecimal.valueOf(80),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        "v1",
                        "v1",
                        "mock"));
        when(turns.submitAnswer(ID, ID + 1, "回答")).thenReturn(submission);
        when(turns.retryTurn(ID)).thenReturn(submission);

        mvc.perform(post("/api/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"COMPREHENSIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.data.id").value(DECIMAL));
        mvc.perform(get("/api/interviews/" + DECIMAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstQuestion.id").value(Long.toString(ID + 1)))
                .andExpect(jsonPath("$.data.currentQuestion.id").value(Long.toString(ID + 1)));
        mvc.perform(post("/api/interviews/" + DECIMAL + "/prepare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interviewId").value(DECIMAL))
                .andExpect(jsonPath("$.data.planId").value(Long.toString(ID + 2)))
                .andExpect(jsonPath("$.data.initialPlanItemId").value(Long.toString(ID + 3)));
        mvc.perform(post("/api/interviews/" + DECIMAL + "/questions/" + (ID + 1) + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"回答\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer.id").value(Long.toString(ID + 4)))
                .andExpect(jsonPath("$.data.evaluation.id").value(Long.toString(ID + 5)))
                .andExpect(jsonPath("$.data.evaluation.profileId").value(Long.toString(ID + 6)))
                .andExpect(jsonPath("$.data.evaluation.overallScore10").isNumber())
                .andExpect(jsonPath("$.data.evaluation.overallScore100").isNumber());
        mvc.perform(post("/api/interviews/" + DECIMAL + "/questions/" + (ID + 1) + "/skip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(DECIMAL));
        mvc.perform(post("/api/interviews/" + DECIMAL + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer.id").value(Long.toString(ID + 4)));
        verify(interviews).getInterview(ID);
        verify(turns).submitAnswer(ID, ID + 1, "回答");
        verify(skips).skipQuestion(ID, ID + 1);
    }

    @Test
    void reviewIdsAreStringsWhileDurationCountsAndUnannotatedLongsStayNumbers() throws Exception {
        var started = LocalDateTime.of(2026, 10, 7, 10, 0);
        when(reviews.getReview(ID))
                .thenReturn(new InterviewReviewResponse(
                        ID,
                        new Overview(started, started.plusSeconds(180), 180L, 1, 1, 0, 0, 2),
                        List.of(new MainQuestionReview(
                                ID + 1,
                                "主问题",
                                Result.UNANSWERED,
                                null,
                                null,
                                List.of(new FollowUpQuestionReview(ID + 2, "追问", Result.UNANSWERED, null, null))))));
        mvc.perform(get("/api/interviews/" + DECIMAL + "/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interviewId").value(DECIMAL))
                .andExpect(jsonPath("$.data.questions[0].questionId").value(Long.toString(ID + 1)))
                .andExpect(
                        jsonPath("$.data.questions[0].followUps[0].questionId").value(Long.toString(ID + 2)))
                .andExpect(jsonPath("$.data.overview.durationSeconds").isNumber())
                .andExpect(jsonPath("$.data.overview.durationSeconds").value(180))
                .andExpect(jsonPath("$.data.overview.mainQuestionCount").isNumber());
        var mapper = new ObjectMapper();
        var plain = mapper.readTree(mapper.writeValueAsString(Map.of("latencyMs", 12L)));
        assertThat(plain.get("latencyMs").isNumber()).isTrue();
    }

    @Test
    void profileGetEditPutConvertsDecimalStringsToLongAndPreservesNewProjectNull() throws Exception {
        var canonical = new InterviewProfileResponse(
                "姓名", "摘要", List.of(new InterviewProfileResponse.ProjectResponse(ID, "项目", "描述")), "岗位", "要求");
        when(profiles.getProfile()).thenReturn(canonical);
        when(profiles.replaceProfile(any())).thenReturn(canonical);
        mvc.perform(get("/api/interview-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].id").value(DECIMAL));
        mvc.perform(put("/api/interview-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody("\"" + DECIMAL + "\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].id").value(DECIMAL));
        var argument = ArgumentCaptor.forClass(InterviewProfileRequest.class);
        verify(profiles).replaceProfile(argument.capture());
        assertThat(argument.getValue().projects().get(0).id()).isEqualTo(ID);
        assertThat(argument.getValue().projects().get(1).id()).isNull();
        var mapper = new ObjectMapper();
        assertThat(mapper.readValue(profileBody("\"9223372036854775807\""), InterviewProfileRequest.class)
                        .projects()
                        .get(0)
                        .id())
                .isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1975511234567890123",
                "1.5",
                "true",
                "{}",
                "[]",
                "\"\"",
                "\"0\"",
                "\"01\"",
                "\"+1\"",
                "\"-1\"",
                "\" 1\"",
                "\"1e3\"",
                "\"9223372036854775808\""
            })
    void rejectsNonStringNonCanonicalAndOverflowIdsBeforeCallingApplication(String token) throws Exception {
        mvc.perform(put("/api/interview-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.code").value(10003));
        verifyNoInteractions(profiles);
    }

    private String profileBody(String idToken) {
        return "{\"candidateName\":\"编辑姓名\",\"resumeSummary\":\"\",\"positionTitle\":\"岗位\","
                + "\"positionDescription\":\"\",\"projects\":[{\"id\":" + idToken
                + ",\"name\":\"原项目\",\"description\":\"\"},{\"id\":null,\"name\":\"新项目\",\"description\":\"\"}]}";
    }
}
