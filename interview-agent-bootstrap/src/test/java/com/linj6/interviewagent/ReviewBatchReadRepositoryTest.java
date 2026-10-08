package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.entity.EvaluationDO;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.entity.EvaluationDimensionScoreDO;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.mapper.EvaluationDimensionScoreMapper;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.mapper.EvaluationMapper;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.repository.EvaluationRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.AnswerDO;
import com.linj6.interviewagent.interview.infrastructure.persistence.mapper.AnswerMapper;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewBatchReadRepositoryTest {
    @Test
    void readsAnswersForInterviewAsSnapshotsAndReturnsEmptyListWhenMissing() {
        AnswerMapper mapper = mock(AnswerMapper.class);
        AnswerDO row = new AnswerDO();
        row.setId(3L);
        row.setInterviewId(1L);
        row.setQuestionId(2L);
        row.setContent("回答");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row), List.of());

        AnswerRepository repository = new AnswerRepository(mapper);
        assertThat(repository.findByInterviewId(1L))
                .containsExactly(
                        new com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerSnapshot(
                                3L, 1L, 2L, "回答"));
        assertThat(repository.findByInterviewId(99L)).isEmpty();
        verify(mapper, org.mockito.Mockito.times(2)).selectList(any(Wrapper.class));
    }

    @Test
    void readsEvaluationsAndDimensionsInTwoBatchQueriesAndMapsOnlyReviewFields() {
        EvaluationMapper evaluationMapper = mock(EvaluationMapper.class);
        EvaluationDimensionScoreMapper dimensionMapper = mock(EvaluationDimensionScoreMapper.class);
        EvaluationDO evaluation = new EvaluationDO();
        evaluation.setId(7L);
        evaluation.setInterviewId(1L);
        evaluation.setQuestionId(2L);
        evaluation.setAnswerId(3L);
        evaluation.setOverallScore10(new BigDecimal("8.5"));
        evaluation.setStrengths("[{\"description\":\"清晰\",\"relatedDimensionCodes\":[]}]");
        evaluation.setWeaknesses("[]");
        evaluation.setMissingPoints("[]");
        evaluation.setReferenceAnswer("{\"answerForm\":\"STAR\",\"keyPoints\":[],\"content\":\"示例\"}");
        EvaluationDO secondEvaluation = new EvaluationDO();
        secondEvaluation.setId(8L);
        secondEvaluation.setInterviewId(1L);
        secondEvaluation.setQuestionId(4L);
        secondEvaluation.setAnswerId(5L);
        secondEvaluation.setOverallScore10(new BigDecimal("6.5"));
        secondEvaluation.setStrengths("[]");
        secondEvaluation.setWeaknesses("[]");
        secondEvaluation.setMissingPoints("[]");
        secondEvaluation.setReferenceAnswer("{\"answerForm\":\"DIRECT\",\"keyPoints\":[],\"content\":\"示例二\"}");
        when(evaluationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(evaluation, secondEvaluation));

        EvaluationDimensionScoreDO dimension = new EvaluationDimensionScoreDO();
        dimension.setEvaluationId(7L);
        dimension.setDimensionName("表达");
        dimension.setApplicability("APPLICABLE");
        dimension.setScore(new BigDecimal("8.0"));
        dimension.setWeight(new BigDecimal("0.5"));
        dimension.setWeightedScore(new BigDecimal("4.0"));
        dimension.setScoreExplanation("结构清楚");
        dimension.setEvidence("[\"内部证据\"]");
        EvaluationDimensionScoreDO secondDimension = new EvaluationDimensionScoreDO();
        secondDimension.setEvaluationId(7L);
        secondDimension.setDimensionName("准确性");
        secondDimension.setApplicability("NOT_APPLICABLE");
        secondDimension.setScore(null);
        secondDimension.setScoreExplanation("本题不适用");
        EvaluationDimensionScoreDO thirdDimension = new EvaluationDimensionScoreDO();
        thirdDimension.setEvaluationId(8L);
        thirdDimension.setDimensionName("深度");
        thirdDimension.setApplicability("APPLICABLE");
        thirdDimension.setScore(new BigDecimal("6.0"));
        thirdDimension.setScoreExplanation("分析较浅");
        when(dimensionMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(dimension, secondDimension, thirdDimension));

        var result =
                new EvaluationRepository(evaluationMapper, dimensionMapper, new ObjectMapper()).findByInterviewId(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).evaluationId()).isEqualTo(7L);
        assertThat(result.get(0).overallScore()).isEqualByComparingTo("8.5");
        assertThat(result.get(0).strengths())
                .singleElement()
                .extracting("description")
                .isEqualTo("清晰");
        assertThat(result.get(0).referenceAnswer().content()).isEqualTo("示例");
        assertThat(result.get(0).dimensionScores()).hasSize(2);
        assertThat(result.get(0).dimensionScores().get(0)).satisfies(score -> {
            assertThat(score.name()).isEqualTo("表达");
            assertThat(score.applicable()).isTrue();
            assertThat(score.score()).isEqualByComparingTo("8.0");
            assertThat(score.explanation()).isEqualTo("结构清楚");
        });
        assertThat(result.get(0).dimensionScores().get(1)).satisfies(score -> {
            assertThat(score.name()).isEqualTo("准确性");
            assertThat(score.applicable()).isFalse();
            assertThat(score.score()).isNull();
            assertThat(score.explanation()).isEqualTo("本题不适用");
        });
        assertThat(result.get(1).evaluationId()).isEqualTo(8L);
        assertThat(result.get(1).dimensionScores()).singleElement().satisfies(score -> {
            assertThat(score.name()).isEqualTo("深度");
            assertThat(score.applicable()).isTrue();
            assertThat(score.score()).isEqualByComparingTo("6.0");
            assertThat(score.explanation()).isEqualTo("分析较浅");
        });
        assertThat(result.get(0).dimensionScores()).extracting("name").containsExactly("表达", "准确性");
        verify(evaluationMapper).selectList(any(Wrapper.class));
        verify(dimensionMapper).selectList(any(Wrapper.class));
    }

    @Test
    void returnsEmptyEvaluationsWithoutQueryingDimensions() {
        EvaluationMapper evaluationMapper = mock(EvaluationMapper.class);
        EvaluationDimensionScoreMapper dimensionMapper = mock(EvaluationDimensionScoreMapper.class);
        when(evaluationMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(new EvaluationRepository(evaluationMapper, dimensionMapper, new ObjectMapper())
                        .findByInterviewId(1L))
                .isEmpty();
        org.mockito.Mockito.verifyNoInteractions(dimensionMapper);
    }
}
