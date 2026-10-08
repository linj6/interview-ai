package com.linj6.interviewagent.position.infrastructure.persistence.repository;

import com.linj6.interviewagent.position.api.PositionInterviewProfile;
import com.linj6.interviewagent.position.api.PositionProfileDataException;
import com.linj6.interviewagent.position.infrastructure.persistence.entity.PositionDO;
import com.linj6.interviewagent.position.infrastructure.persistence.mapper.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Position 持久化仓储。 */
@Repository
@RequiredArgsConstructor
public class PositionRepository {
    private final PositionMapper positionMapper;

    public void replaceProfile(Long positionId, PositionInterviewProfile profile) {
        int rows = positionMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PositionDO>()
                        .eq(PositionDO::getId, positionId)
                        .set(PositionDO::getTitle, profile.title())
                        .set(PositionDO::getDescription, profile.description()));
        if (rows != 1) throw new PositionProfileDataException("Current Position 关键更新未命中唯一记录");
    }

    public PositionInterviewProfile getInterviewProfile(Long positionId) {
        PositionDO position = positionMapper.selectById(positionId);
        if (position == null) {
            throw new PositionProfileDataException("Current Position 不存在");
        }
        return new PositionInterviewProfile(position.getTitle(), position.getDescription());
    }
}
