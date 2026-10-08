package com.linj6.interviewagent.evaluation.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.evaluation.api.evaluation.DimensionScore;
import com.linj6.interviewagent.evaluation.api.evaluation.DimensionScoreDTO;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationCommand;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationCreated;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationDTO;
import com.linj6.interviewagent.evaluation.api.evaluation.Feedback;
import com.linj6.interviewagent.evaluation.api.evaluation.MissingPoint;
import com.linj6.interviewagent.evaluation.api.evaluation.ReferenceAnswer;
import com.linj6.interviewagent.evaluation.domain.EvaluationErrorCode;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.entity.EvaluationDO;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.entity.EvaluationDimensionScoreDO;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.mapper.EvaluationDimensionScoreMapper;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.mapper.EvaluationMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 正式 Evaluation 及维度分数的持久化仓储。 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EvaluationRepository {
    private final EvaluationMapper evaluationMapper;
    private final EvaluationDimensionScoreMapper dimensionScoreMapper;
    private final ObjectMapper objectMapper;

    public EvaluationCreated create(EvaluationCommand command) {
        EvaluationDO evaluation = toEvaluation(command);
        try {
            evaluationMapper.insert(evaluation);
        } catch (DuplicateKeyException exception) {
            log.warn(
                    "正式 Evaluation 重复写入 interviewId={} questionId={} answerId={} failureType=DUPLICATE_KEY",
                    command.interviewId(),
                    command.questionId(),
                    command.answerId());
            throw new BusinessException(EvaluationErrorCode.EVALUATION_ALREADY_EXISTS);
        } catch (RuntimeException exception) {
            log.error(
                    "正式 Evaluation 持久化失败 interviewId={} questionId={} answerId={} failureType={}",
                    command.interviewId(),
                    command.questionId(),
                    command.answerId(),
                    exception.getClass().getSimpleName());
            throw exception;
        }
        try {
            for (DimensionScore dimension : command.dimensions()) {
                dimensionScoreMapper.insert(toDimensionScore(evaluation.getId(), dimension));
            }
            return new EvaluationCreated(evaluation.getId());
        } catch (RuntimeException exception) {
            log.error(
                    "正式 Evaluation 维度持久化失败 interviewId={} questionId={} answerId={} failureType={}",
                    command.interviewId(),
                    command.questionId(),
                    command.answerId(),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    public List<EvaluationDTO> findByInterviewId(Long interviewId) {
        List<EvaluationDO> evaluations = evaluationMapper.selectList(Wrappers.<EvaluationDO>lambdaQuery()
                .eq(EvaluationDO::getInterviewId, interviewId)
                .orderByAsc(EvaluationDO::getId));
        if (evaluations.isEmpty()) {
            return List.of();
        }

        List<Long> evaluationIds = evaluations.stream().map(EvaluationDO::getId).toList();
        Map<Long, List<DimensionScoreDTO>> dimensionsByEvaluationId = dimensionScoreMapper
                .selectList(Wrappers.<EvaluationDimensionScoreDO>lambdaQuery()
                        .in(EvaluationDimensionScoreDO::getEvaluationId, evaluationIds)
                        .orderByAsc(EvaluationDimensionScoreDO::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        EvaluationDimensionScoreDO::getEvaluationId,
                        Collectors.mapping(this::toDimensionScoreDTO, Collectors.toList())));

        return evaluations.stream()
                .map(value -> new EvaluationDTO(
                        value.getId(),
                        value.getInterviewId(),
                        value.getQuestionId(),
                        value.getAnswerId(),
                        value.getOverallScore10(),
                        readJson(value.getStrengths(), new TypeReference<List<Feedback>>() {}),
                        readJson(value.getWeaknesses(), new TypeReference<List<Feedback>>() {}),
                        readJson(value.getMissingPoints(), new TypeReference<List<MissingPoint>>() {}),
                        readJson(value.getReferenceAnswer(), new TypeReference<ReferenceAnswer>() {}),
                        dimensionsByEvaluationId.getOrDefault(value.getId(), List.of())))
                .toList();
    }

    private DimensionScoreDTO toDimensionScoreDTO(EvaluationDimensionScoreDO value) {
        return new DimensionScoreDTO(
                value.getDimensionName(),
                "APPLICABLE".equals(value.getApplicability()),
                value.getScore(),
                value.getScoreExplanation());
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法反序列化 Evaluation JSON", exception);
        }
    }

    private EvaluationDO toEvaluation(EvaluationCommand command) {
        EvaluationDO value = new EvaluationDO();
        value.setInterviewId(command.interviewId());
        value.setQuestionId(command.questionId());
        value.setAnswerId(command.answerId());
        value.setProfileId(command.profileId());
        value.setOverallScore10(command.overallScore10());
        value.setOverallScore100(command.overallScore100());
        value.setStrengths(json(command.strengths()));
        value.setWeaknesses(json(command.weaknesses()));
        value.setMissingPoints(json(command.missingPoints()));
        value.setReferenceAnswer(json(command.referenceAnswer()));
        value.setPromptVersion(command.promptVersion());
        value.setEvaluatorVersion(command.evaluatorVersion());
        value.setModel(command.model());
        value.setInputTokens(command.inputTokens());
        value.setOutputTokens(command.outputTokens());
        value.setTotalTokens(command.totalTokens());
        value.setLatencyMs(command.latencyMs());
        return value;
    }

    private EvaluationDimensionScoreDO toDimensionScore(Long evaluationId, DimensionScore dimension) {
        EvaluationDimensionScoreDO value = new EvaluationDimensionScoreDO();
        value.setEvaluationId(evaluationId);
        value.setDimensionCode(dimension.dimensionCode());
        value.setDimensionName(dimension.dimensionName());
        value.setApplicability(dimension.applicability());
        value.setScore(dimension.score());
        value.setWeight(dimension.weight());
        value.setWeightedScore(dimension.weightedScore());
        value.setScoreExplanation(dimension.scoreExplanation());
        value.setEvidence(json(dimension.evidence()));
        return value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化正式 Evaluation JSON", exception);
        }
    }
}
