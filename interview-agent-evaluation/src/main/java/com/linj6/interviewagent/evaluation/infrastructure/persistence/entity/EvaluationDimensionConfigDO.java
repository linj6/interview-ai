package com.linj6.interviewagent.evaluation.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** evaluation_dimension_config 表映射对象。 */
@Getter
@Setter
@TableName("evaluation_dimension_config")
public class EvaluationDimensionConfigDO {
    /** 维度配置标识。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属 Evaluation Profile 标识。 */
    private Long profileId;
    /** 稳定维度代码。 */
    private String dimensionCode;
    /** 维度名称。 */
    private String dimensionName;
    /** 评价标准。 */
    private String evaluationCriteria;
    /** 评分指引。 */
    private String scoreGuidance;
    /** Java 确定性计算使用的原始权重。 */
    private BigDecimal weight;
    /** 是否允许返回 NOT_APPLICABLE。 */
    private Boolean naAllowed;
    /** Profile 内展示和计算顺序。 */
    private Integer sortOrder;
}
