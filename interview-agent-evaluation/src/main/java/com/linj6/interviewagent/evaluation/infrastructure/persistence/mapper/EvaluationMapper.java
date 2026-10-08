package com.linj6.interviewagent.evaluation.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.entity.EvaluationDO;
import org.apache.ibatis.annotations.Mapper;

/** Evaluation 的 MyBatis-Plus Mapper。 */
@Mapper
public interface EvaluationMapper extends BaseMapper<EvaluationDO> {}
