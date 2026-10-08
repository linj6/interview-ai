package com.linj6.interviewagent.interview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** interview_plan 表映射。 */
@Getter
@Setter
@TableName("interview_plan")
public class InterviewPlanDO {
    /** 正式 Plan 标识。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 所属 Interview 标识。 */
    private Long interviewId;
    /** 本 Plan 内首个考察项的真实标识。 */
    private Long initialPlanItemId;
}
