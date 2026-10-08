package com.linj6.interviewagent.interview.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.InterviewPlanDO;
import org.apache.ibatis.annotations.Mapper;

/** InterviewPlan 的 MyBatis-Plus Mapper。 */
@Mapper
public interface InterviewPlanMapper extends BaseMapper<InterviewPlanDO> {}
