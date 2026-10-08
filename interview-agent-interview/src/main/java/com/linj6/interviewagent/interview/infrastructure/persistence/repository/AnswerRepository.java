package com.linj6.interviewagent.interview.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.AnswerDO;
import com.linj6.interviewagent.interview.infrastructure.persistence.mapper.AnswerMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 不可变 Answer 持久化仓储。 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AnswerRepository {

    private final AnswerMapper answerMapper;

    public AnswerSnapshot save(Long interviewId, Long questionId, String content) {
        AnswerDO value = new AnswerDO();
        value.setInterviewId(interviewId);
        value.setQuestionId(questionId);
        value.setContent(content);
        try {
            answerMapper.insert(value);
            return toSnapshot(value);
        } catch (DuplicateKeyException exception) {
            log.warn("Answer 重复提交 interviewId={} questionId={} failureType=DUPLICATE_KEY", interviewId, questionId);
            throw new BusinessException(InterviewErrorCode.ANSWER_ALREADY_EXISTS);
        } catch (RuntimeException exception) {
            log.error(
                    "Answer 持久化失败 interviewId={} questionId={} failureType={}",
                    interviewId,
                    questionId,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    public AnswerSnapshot findById(Long answerId) {
        AnswerDO value = answerMapper.selectById(answerId);
        if (value == null) {
            throw new BusinessException(InterviewErrorCode.ANSWER_NOT_FOUND);
        }
        return toSnapshot(value);
    }

    public AnswerSnapshot findByInterviewIdAndQuestionId(Long interviewId, Long questionId) {
        AnswerDO value = answerMapper.selectOne(Wrappers.<AnswerDO>lambdaQuery()
                .eq(AnswerDO::getInterviewId, interviewId)
                .eq(AnswerDO::getQuestionId, questionId));
        if (value == null) {
            throw new BusinessException(InterviewErrorCode.ANSWER_NOT_FOUND);
        }
        return toSnapshot(value);
    }

    public List<AnswerSnapshot> findByInterviewId(Long interviewId) {
        return answerMapper
                .selectList(Wrappers.<AnswerDO>lambdaQuery()
                        .eq(AnswerDO::getInterviewId, interviewId)
                        .orderByAsc(AnswerDO::getId))
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    private AnswerSnapshot toSnapshot(AnswerDO value) {
        return new AnswerSnapshot(value.getId(), value.getInterviewId(), value.getQuestionId(), value.getContent());
    }
}
