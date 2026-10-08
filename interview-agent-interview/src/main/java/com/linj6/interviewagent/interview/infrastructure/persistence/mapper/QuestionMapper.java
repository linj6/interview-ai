package com.linj6.interviewagent.interview.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.interview.domain.QuestionStatus;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.QuestionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Question 的 MyBatis-Plus Mapper。 */
@Mapper
public interface QuestionMapper extends BaseMapper<QuestionDO> {
    int markAnswered(
            @Param("id") Long id,
            @Param("interviewId") Long interviewId,
            @Param("expectedStatus") QuestionStatus expectedStatus,
            @Param("targetStatus") QuestionStatus targetStatus);

    int markSkipped(
            @Param("id") Long id,
            @Param("interviewId") Long interviewId,
            @Param("expectedStatus") QuestionStatus expectedStatus,
            @Param("targetStatus") QuestionStatus targetStatus);
}
