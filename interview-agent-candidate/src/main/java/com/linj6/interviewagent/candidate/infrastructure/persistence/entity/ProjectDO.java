package com.linj6.interviewagent.candidate.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 简历项目持久化事实。 */
@Getter
@Setter
@TableName("candidate_project")
public class ProjectDO {
    /** 项目 ID，读取时的稳定排序依据。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 所属简历 ID。 */
    private Long resumeId;
    /** 项目名称。 */
    private String name;
    /** 项目描述。 */
    private String description;
}
