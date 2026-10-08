package com.linj6.interviewagent.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.candidate.api.CandidateQueryApi;
import com.linj6.interviewagent.evaluation.api.profile.EvaluationProfileApi;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import com.linj6.interviewagent.position.api.PositionQueryApi;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class InterviewExpirationConvergenceTest {

    @Test
    void doesNotClaimExpirationWhenCasLosesToConcurrentFinish() {
        InterviewRepository interviews = mock(InterviewRepository.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        InterviewSnapshot expiredWaiting = new InterviewSnapshot(
                7L,
                1L,
                1L,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.WAITING_ANSWER,
                4L,
                LocalDateTime.parse("2026-09-26T11:45:00"));
        InterviewSnapshot finishedByAnotherOperation =
                new InterviewSnapshot(7L, 1L, 1L, InterviewStatus.COMPLETED, null, 5L, expiredWaiting.expiresAt());
        given(interviews.findById(7L)).willReturn(expiredWaiting, expiredWaiting, finishedByAnotherOperation);
        given(interviews.completeExpiredWaiting(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(InterviewStatus.IN_PROGRESS),
                        org.mockito.ArgumentMatchers.eq(4L),
                        any()))
                .willReturn(false);
        given(transactions.execute(any(TransactionCallback.class))).willAnswer(invocation -> invocation
                .<TransactionCallback<Boolean>>getArgument(0)
                .doInTransaction(new SimpleTransactionStatus()));

        InterviewApplicationService service = new InterviewApplicationService(
                interviews,
                mock(QuestionRepository.class),
                mock(
                        com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewPlanRepository
                                .class),
                mock(EvaluationProfileApi.class),
                transactions,
                mock(CandidateQueryApi.class),
                mock(PositionQueryApi.class),
                mock(InterviewAiApi.class),
                Clock.fixed(Instant.parse("2026-09-26T03:45:00Z"), ZoneId.of("Asia/Shanghai")),
                new InterviewDurationSettings(60));

        assertThat(service.convergeExpiredWaiting(7L)).isFalse();
    }
}
