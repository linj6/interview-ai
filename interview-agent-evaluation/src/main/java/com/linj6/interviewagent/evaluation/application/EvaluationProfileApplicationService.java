package com.linj6.interviewagent.evaluation.application;

import com.linj6.interviewagent.evaluation.api.profile.EvaluationProfile;
import com.linj6.interviewagent.evaluation.api.profile.EvaluationProfileApi;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.repository.EvaluationProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Evaluation Profile Public API 的最小应用层实现。 */
@Service
@RequiredArgsConstructor
public class EvaluationProfileApplicationService implements EvaluationProfileApi {
    private final EvaluationProfileRepository evaluationProfileRepository;

    @Override
    public EvaluationProfile findById(Long profileId) {
        return evaluationProfileRepository.findById(profileId);
    }

    @Override
    public EvaluationProfile findByCode(String profileCode) {
        return evaluationProfileRepository.findByCode(profileCode);
    }
}
