package com.linj6.interviewagent.evaluation.application;

import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationApi;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationCommand;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationCreated;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationDTO;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.repository.EvaluationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Evaluation 模块 Public API 的最小应用层实现。 */
@Service
@RequiredArgsConstructor
public class EvaluationApplicationService implements EvaluationApi {
    private final EvaluationRepository evaluationRepository;

    @Override
    public EvaluationCreated create(EvaluationCommand command) {
        return evaluationRepository.create(command);
    }

    @Override
    public List<EvaluationDTO> findByInterviewId(Long interviewId) {
        return evaluationRepository.findByInterviewId(interviewId);
    }
}
