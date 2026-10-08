package com.linj6.interviewagent.evaluation.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.entity.EvaluationProfileDO;
import org.apache.ibatis.annotations.Mapper;

/** Evaluation Profile 的 MyBatis-Plus Mapper。 */
@Mapper
public interface EvaluationProfileMapper extends BaseMapper<EvaluationProfileDO> {}
