package com.linj6.interviewagent.candidate.application;

import com.linj6.interviewagent.candidate.api.*;
import com.linj6.interviewagent.candidate.infrastructure.persistence.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 资料管理 Public API 实现。 */
@Service
@RequiredArgsConstructor
public class CandidateProfileService implements CandidateProfileApi {
    private final CandidateProfileRepository repository;

    public CandidateManagedProfile getProfile(Long candidateId) {
        return repository.getProfile(candidateId);
    }

    public void replaceProfile(Long candidateId, CandidateManagedProfile profile) {
        repository.replaceProfile(candidateId, profile);
    }
}
