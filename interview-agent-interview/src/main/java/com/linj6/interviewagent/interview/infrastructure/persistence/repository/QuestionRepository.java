package com.linj6.interviewagent.interview.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.domain.QuestionStatus;
import com.linj6.interviewagent.interview.domain.QuestionType;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.QuestionDO;
import com.linj6.interviewagent.interview.infrastructure.persistence.mapper.QuestionMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/** Question 持久化仓储。 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class QuestionRepository {

    private final QuestionMapper questionMapper;

    public QuestionSnapshot findById(Long id) {
        QuestionDO value = questionMapper.selectById(id);
        if (value == null) {
            throw new BusinessException(InterviewErrorCode.QUESTION_NOT_FOUND);
        }
        return toSnapshot(value);
    }

    public QuestionSnapshot findFirstByInterviewId(Long interviewId) {
        QuestionDO value = questionMapper
                .selectList(Wrappers.<QuestionDO>lambdaQuery()
                        .eq(QuestionDO::getInterviewId, interviewId)
                        .orderByAsc(QuestionDO::getSequenceNo))
                .stream()
                .findFirst()
                .orElse(null);
        return value == null ? null : toSnapshot(value);
    }

    public QuestionSnapshot findLatestWaitingByInterviewId(Long interviewId) {
        QuestionDO value = questionMapper.selectOne(Wrappers.<QuestionDO>lambdaQuery()
                .eq(QuestionDO::getInterviewId, interviewId)
                .eq(QuestionDO::getStatus, QuestionStatus.WAITING_ANSWER)
                .orderByDesc(QuestionDO::getSequenceNo)
                .last("LIMIT 1"));
        return value == null ? null : toSnapshot(value);
    }

    public List<QuestionSnapshot> findWaitingByInterviewId(Long interviewId) {
        return questionMapper
                .selectList(Wrappers.<QuestionDO>lambdaQuery()
                        .eq(QuestionDO::getInterviewId, interviewId)
                        .eq(QuestionDO::getStatus, QuestionStatus.WAITING_ANSWER))
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    public List<QuestionSnapshot> findByInterviewId(Long interviewId) {
        return questionMapper
                .selectList(Wrappers.<QuestionDO>lambdaQuery()
                        .eq(QuestionDO::getInterviewId, interviewId)
                        .orderByAsc(QuestionDO::getSequenceNo))
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    public QuestionSnapshot create(QuestionCreateCommand command) {
        if (command.planItemId() == null) {
            throw new IllegalArgumentException("Question requires a real PlanItem ID");
        }
        QuestionDO value = new QuestionDO();
        value.setInterviewId(command.interviewId());
        value.setSequenceNo(command.sequenceNo());
        value.setParentQuestionId(command.parentQuestionId());
        value.setType(command.type().name());
        value.setStatus(QuestionStatus.WAITING_ANSWER);
        value.setContent(command.content());
        value.setEvaluationProfileId(command.evaluationProfileId());
        value.setTopicId(command.topicId());
        value.setPlanItemId(command.planItemId());
        questionMapper.insert(value);
        return toSnapshot(value);
    }

    public void markAnswered(Long questionId, Long interviewId) {
        if (questionMapper.markAnswered(questionId, interviewId, QuestionStatus.WAITING_ANSWER, QuestionStatus.ANSWERED)
                == 1) {
            return;
        }
        findById(questionId);
        log.warn("Question 状态流转冲突 interviewId={} questionId={} failureType=STATE_CONFLICT", interviewId, questionId);
        throw new BusinessException(InterviewErrorCode.QUESTION_STATE_CONFLICT);
    }

    public void markSkipped(Long questionId, Long interviewId) {
        if (questionMapper.markSkipped(questionId, interviewId, QuestionStatus.WAITING_ANSWER, QuestionStatus.SKIPPED)
                == 1) {
            return;
        }
        findById(questionId);
        log.warn("Question Skip 冲突 interviewId={} questionId={}", interviewId, questionId);
        throw new BusinessException(InterviewErrorCode.QUESTION_STATE_CONFLICT);
    }

    private QuestionSnapshot toSnapshot(QuestionDO value) {
        return new QuestionSnapshot(
                value.getId(),
                value.getInterviewId(),
                value.getSequenceNo(),
                value.getParentQuestionId(),
                QuestionType.valueOf(value.getType()),
                value.getStatus(),
                value.getContent(),
                value.getEvaluationProfileId(),
                value.getTopicId(),
                value.getPlanItemId());
    }
}
