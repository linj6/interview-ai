package com.linj6.interviewagent.interview.interfaces.controller;

import com.linj6.interviewagent.common.api.Result;
import com.linj6.interviewagent.interview.application.InterviewApplicationService;
import com.linj6.interviewagent.interview.application.InterviewPrepareApplicationService;
import com.linj6.interviewagent.interview.application.InterviewReviewApplicationService;
import com.linj6.interviewagent.interview.application.InterviewSkipApplicationService;
import com.linj6.interviewagent.interview.application.InterviewTurnApplicationService;
import com.linj6.interviewagent.interview.interfaces.request.AnswerSubmissionRequest;
import com.linj6.interviewagent.interview.interfaces.request.InterviewCreateRequest;
import com.linj6.interviewagent.interview.interfaces.response.AnswerSubmissionResponse;
import com.linj6.interviewagent.interview.interfaces.response.InterviewDetailResponse;
import com.linj6.interviewagent.interview.interfaces.response.InterviewPrepareResponse;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final InterviewPrepareApplicationService prepareApplicationService;
    private final InterviewApplicationService applicationService;
    private final InterviewTurnApplicationService turnApplicationService;
    private final InterviewSkipApplicationService skipApplicationService;
    private final InterviewReviewApplicationService reviewApplicationService;

    public InterviewController(
            InterviewPrepareApplicationService prepareApplicationService,
            InterviewApplicationService applicationService,
            InterviewTurnApplicationService turnApplicationService,
            InterviewSkipApplicationService skipApplicationService,
            InterviewReviewApplicationService reviewApplicationService) {
        this.prepareApplicationService = prepareApplicationService;
        this.applicationService = applicationService;
        this.turnApplicationService = turnApplicationService;
        this.skipApplicationService = skipApplicationService;
        this.reviewApplicationService = reviewApplicationService;
    }

    @PostMapping
    public Result<InterviewDetailResponse> createInterview(@Valid @RequestBody InterviewCreateRequest request) {
        return Result.success(applicationService.createInterview(request.mode(), request.focusArea()));
    }

    @PostMapping("/{interviewId}/prepare")
    public Result<InterviewPrepareResponse> prepareInterview(@PathVariable Long interviewId) {
        return Result.success(prepareApplicationService.prepareInterview(interviewId));
    }

    @PostMapping("/{interviewId}/start")
    public Result<InterviewDetailResponse> startInterview(@PathVariable Long interviewId) {
        return Result.success(applicationService.startInterview(interviewId));
    }

    @PostMapping("/{interviewId}/pause")
    public Result<InterviewDetailResponse> pauseInterview(@PathVariable Long interviewId) {
        return Result.success(applicationService.pauseInterview(interviewId));
    }

    @PostMapping("/{interviewId}/resume")
    public Result<InterviewDetailResponse> resumeInterview(@PathVariable Long interviewId) {
        return Result.success(applicationService.resumeInterview(interviewId));
    }

    @PostMapping("/{interviewId}/finish")
    public Result<InterviewDetailResponse> finishInterview(@PathVariable Long interviewId) {
        return Result.success(applicationService.finishInterview(interviewId));
    }

    @PostMapping("/{interviewId}/questions/{questionId}/answer")
    public Result<AnswerSubmissionResponse> submitAnswer(
            @PathVariable Long interviewId,
            @PathVariable Long questionId,
            @Valid @RequestBody AnswerSubmissionRequest request) {
        return Result.success(turnApplicationService.submitAnswer(interviewId, questionId, request.content()));
    }

    @PostMapping("/{interviewId}/retry")
    public Result<AnswerSubmissionResponse> retryTurn(@PathVariable Long interviewId) {
        return Result.success(turnApplicationService.retryTurn(interviewId));
    }

    @PostMapping("/{interviewId}/questions/{questionId}/skip")
    public Result<InterviewDetailResponse> skipQuestion(@PathVariable Long interviewId, @PathVariable Long questionId) {
        return Result.success(skipApplicationService.skipQuestion(interviewId, questionId));
    }

    @GetMapping("/{interviewId}")
    public Result<InterviewDetailResponse> getInterview(@PathVariable Long interviewId) {
        return Result.success(applicationService.getInterview(interviewId));
    }

    @GetMapping("/{interviewId}/review")
    public Result<InterviewReviewResponse> getReview(@PathVariable Long interviewId) {
        return Result.success(reviewApplicationService.getReview(interviewId));
    }
}
