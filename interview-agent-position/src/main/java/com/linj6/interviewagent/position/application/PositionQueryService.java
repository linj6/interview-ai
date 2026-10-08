package com.linj6.interviewagent.position.application;

import com.linj6.interviewagent.position.api.PositionInterviewProfile;
import com.linj6.interviewagent.position.api.PositionQueryApi;
import com.linj6.interviewagent.position.infrastructure.persistence.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Position 模块面向 Interview 的查询能力实现。 */
@Service
@RequiredArgsConstructor
public class PositionQueryService implements PositionQueryApi {
    private final PositionRepository positionRepository;

    @Override
    public PositionInterviewProfile getInterviewProfile(Long positionId) {
        return positionRepository.getInterviewProfile(positionId);
    }
}
