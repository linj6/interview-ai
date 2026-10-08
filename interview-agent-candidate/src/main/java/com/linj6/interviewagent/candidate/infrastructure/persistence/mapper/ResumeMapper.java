package com.linj6.interviewagent.candidate.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.candidate.infrastructure.persistence.entity.ResumeDO;
import org.apache.ibatis.annotations.Mapper;

/** 简历持久化 Mapper。 */
@Mapper
public interface ResumeMapper extends BaseMapper<ResumeDO> {}
