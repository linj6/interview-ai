package com.linj6.interviewagent.interview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linj6.interviewagent.interview.domain.KnowledgeArea;
import lombok.Getter;
import lombok.Setter;

/** interview_plan_item 表映射。 */
@Getter
@Setter
@TableName("interview_plan_item")
public class PlanItemDO {
    /** 正式 PlanItem 标识。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 所属 Plan 标识。 */
    private Long planId;
    /** 知识领域。 */
    private KnowledgeArea area;
    /** 唯一考察焦点事实。 */
    private String focus;
    /** 选择该考察项的理由。 */
    private String reason;
}
