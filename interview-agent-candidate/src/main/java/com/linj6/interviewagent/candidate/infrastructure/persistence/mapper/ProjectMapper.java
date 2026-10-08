package com.linj6.interviewagent.candidate.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.candidate.infrastructure.persistence.entity.ProjectDO;
import org.apache.ibatis.annotations.Mapper;

/** 简历项目的基础查询 Mapper。 */
@Mapper
public interface ProjectMapper extends BaseMapper<ProjectDO> {}
