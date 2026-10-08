package com.linj6.interviewagent.interview.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.AnswerDO;
import org.apache.ibatis.annotations.Mapper;

/** Answer 的 MyBatis-Plus Mapper。 */
@Mapper
public interface AnswerMapper extends BaseMapper<AnswerDO> {}
