package com.linj6.interviewagent.evaluation.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** evaluation_profile 表映射对象。 */
@Getter
@Setter
@TableName("evaluation_profile")
public class EvaluationProfileDO {
    /** Evaluation Profile 标识。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 稳定业务代码。 */
    private String code;
    /** Profile 名称。 */
    private String name;
    /** Profile 业务说明。 */
    private String description;
    /** Profile 创建时间。 */
    private LocalDateTime createdAt;
}
