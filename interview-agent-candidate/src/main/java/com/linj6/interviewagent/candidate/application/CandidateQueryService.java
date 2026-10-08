package com.linj6.interviewagent.candidate.application;

import com.linj6.interviewagent.candidate.api.CandidateInterviewProfile;
import com.linj6.interviewagent.candidate.api.CandidateQueryApi;
import com.linj6.interviewagent.candidate.infrastructure.persistence.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Candidate 模块面向 Interview 的查询能力实现。 */
@Service
@RequiredArgsConstructor
public class CandidateQueryService implements CandidateQueryApi {
    private final CandidateRepository candidateRepository;

    @Override
    public CandidateInterviewProfile getInterviewProfile(Long candidateId) {
        return candidateRepository.getInterviewProfile(candidateId);
    }
}
