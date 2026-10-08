package com.linj6.interviewagent.position.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.position.infrastructure.persistence.entity.PositionDO;
import org.apache.ibatis.annotations.Mapper;

/** Position 的 MyBatis-Plus Mapper。 */
@Mapper
public interface PositionMapper extends BaseMapper<PositionDO> {}
