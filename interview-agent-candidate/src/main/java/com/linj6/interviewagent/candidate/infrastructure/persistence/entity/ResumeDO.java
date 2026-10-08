package com.linj6.interviewagent.candidate.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

/** 当前简历持久化事实。 */
@Getter
@Setter
@TableName("candidate_resume")
public class ResumeDO {
    @TableId(type = IdType.INPUT)
    private Long id;

    private Long candidateId;
    private String summary;
}
