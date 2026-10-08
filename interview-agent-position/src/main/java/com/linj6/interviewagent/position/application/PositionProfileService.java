package com.linj6.interviewagent.position.application;

import com.linj6.interviewagent.position.api.*;
import com.linj6.interviewagent.position.infrastructure.persistence.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 当前岗位资料写能力。 */
@Service
@RequiredArgsConstructor
public class PositionProfileService implements PositionProfileApi {
    private final PositionRepository repository;

    public void replaceProfile(Long positionId, PositionInterviewProfile profile) {
        repository.replaceProfile(positionId, profile);
    }
}
