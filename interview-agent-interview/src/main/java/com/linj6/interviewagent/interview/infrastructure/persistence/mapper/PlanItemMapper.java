package com.linj6.interviewagent.interview.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.PlanItemDO;
import org.apache.ibatis.annotations.Mapper;

/** PlanItem 的 MyBatis-Plus Mapper。 */
@Mapper
public interface PlanItemMapper extends BaseMapper<PlanItemDO> {}
