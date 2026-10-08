package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.QuestionStatus;
import com.linj6.interviewagent.interview.domain.QuestionType;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.InterviewDO;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.QuestionDO;
import com.linj6.interviewagent.interview.infrastructure.persistence.mapper.InterviewMapper;
import com.linj6.interviewagent.interview.infrastructure.persistence.mapper.QuestionMapper;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionCreateCommand;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TurnRepositoryTest {
    @Test
    void createsWaitingQuestionWithAllProvidedFields() {
        QuestionMapper mapper = mock(QuestionMapper.class);
        QuestionRepository repository = new QuestionRepository(mapper);
        var created = repository.create(QuestionCreateCommand.builder()
                .interviewId(1L)
                .sequenceNo(5)
                .parentQuestionId(2L)
                .type(QuestionType.FOLLOW_UP)
                .content("追问")
                .evaluationProfileId(7L)
                .topicId(8L)
                .planItemId(9L)
                .build());
        assertThat(created.planItemId()).isEqualTo(9L);
        assertThat(created.type()).isEqualTo(QuestionType.FOLLOW_UP);

        ArgumentCaptor<QuestionDO> inserted = ArgumentCaptor.forClass(QuestionDO.class);
        verify(mapper).insert(inserted.capture());
        QuestionDO question = inserted.getValue();
        assertThat(question.getInterviewId()).isEqualTo(1L);
        assertThat(question.getSequenceNo()).isEqualTo(5);
        assertThat(question.getParentQuestionId()).isEqualTo(2L);
        assertThat(question.getType()).isEqualTo("FOLLOW_UP");
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.WAITING_ANSWER);
        assertThat(question.getContent()).isEqualTo("追问");
        assertThat(question.getEvaluationProfileId()).isEqualTo(7L);
        assertThat(question.getTopicId()).isEqualTo(8L);
        assertThat(question.getPlanItemId()).isEqualTo(9L);
    }

    @Test
    void requiresPlanItemForBothQuestionTypesBeforeInsert() {
        QuestionMapper mapper = mock(QuestionMapper.class);
        QuestionRepository repository = new QuestionRepository(mapper);
        for (QuestionType type : QuestionType.values()) {
            assertThatThrownBy(() -> repository.create(QuestionCreateCommand.builder()
                            .interviewId(1L)
                            .sequenceNo(1)
                            .type(type)
                            .content("问题")
                            .evaluationProfileId(7L)
                            .build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PlanItem");
        }
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(QuestionDO.class));
    }

    @Test
    void persistsMainWithRealPlanItem() {
        QuestionMapper mapper = mock(QuestionMapper.class);
        QuestionRepository repository = new QuestionRepository(mapper);
        assertThat(repository
                        .create(QuestionCreateCommand.builder()
                                .interviewId(1L)
                                .sequenceNo(1)
                                .type(QuestionType.MAIN)
                                .content("主问题")
                                .evaluationProfileId(7L)
                                .planItemId(9L)
                                .build())
                        .planItemId())
                .isEqualTo(9L);
        ArgumentCaptor<QuestionDO> inserted = ArgumentCaptor.forClass(QuestionDO.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getPlanItemId()).isEqualTo(9L);
    }

    @Test
    void mapsStoredQuestionTypeStringBackToEnum() {
        QuestionMapper mapper = mock(QuestionMapper.class);
        QuestionDO row = new QuestionDO();
        row.setId(1L);
        row.setInterviewId(1L);
        row.setSequenceNo(1);
        row.setType("MAIN");
        row.setStatus(QuestionStatus.WAITING_ANSWER);
        row.setEvaluationProfileId(7L);
        row.setTopicId(8L);
        row.setPlanItemId(9L);
        when(mapper.selectById(1L)).thenReturn(row);

        var snapshot = new QuestionRepository(mapper).findById(1L);
        assertThat(snapshot.type()).isEqualTo(QuestionType.MAIN);
        assertThat(snapshot.evaluationProfileId()).isEqualTo(7L);
        assertThat(snapshot.topicId()).isEqualTo(8L);
        assertThat(snapshot.planItemId()).isEqualTo(9L);
    }

    @Test
    void usesExpectedProcessingStateAndVersionForBothTurnExits() {
        InterviewMapper mapper = mock(InterviewMapper.class);
        InterviewRepository repository = new InterviewRepository(mapper);
        InterviewDO waiting = interview(InterviewStatus.IN_PROGRESS, InterviewPhase.WAITING_ANSWER, 7L);
        InterviewDO completed = interview(InterviewStatus.COMPLETED, null, 7L);
        when(mapper.markWaitingAfterTurn(
                        1L,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.PROCESSING_TURN,
                        InterviewPhase.WAITING_ANSWER,
                        6L))
                .thenReturn(1);
        LocalDateTime completedAt = LocalDateTime.parse("2026-09-26T12:00:00");
        when(mapper.completeAfterTurn(
                        1L,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.PROCESSING_TURN,
                        InterviewStatus.COMPLETED,
                        6L,
                        completedAt))
                .thenReturn(1);
        when(mapper.selectById(1L)).thenReturn(waiting, completed);

        assertThat(repository.markWaitingAfterTurn(1L, 6L).currentPhase()).isEqualTo(InterviewPhase.WAITING_ANSWER);
        assertThat(repository.completeAfterTurn(1L, 6L, completedAt).status()).isEqualTo(InterviewStatus.COMPLETED);
        verify(mapper)
                .markWaitingAfterTurn(
                        1L,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.PROCESSING_TURN,
                        InterviewPhase.WAITING_ANSWER,
                        6L);
        verify(mapper)
                .completeAfterTurn(
                        1L,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.PROCESSING_TURN,
                        InterviewStatus.COMPLETED,
                        6L,
                        completedAt);
    }

    private InterviewDO interview(InterviewStatus status, InterviewPhase phase, Long version) {
        InterviewDO value = new InterviewDO();
        value.setId(1L);
        value.setStatus(status);
        value.setCurrentPhase(phase);
        value.setVersion(version);
        return value;
    }
}
