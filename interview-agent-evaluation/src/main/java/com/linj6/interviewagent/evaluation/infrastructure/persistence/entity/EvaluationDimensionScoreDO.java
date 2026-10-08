package com.linj6.interviewagent.evaluation.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** evaluation_dimension_score 表映射对象。 */
@Getter
@Setter
@TableName("evaluation_dimension_score")
public class EvaluationDimensionScoreDO {
    /** 维度评分标识。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属正式 Evaluation 标识。 */
    private Long evaluationId;
    /** 维度稳定代码快照。 */
    private String dimensionCode;
    /** 维度名称快照。 */
    private String dimensionName;
    /** APPLICABLE 或 NOT_APPLICABLE。 */
    private String applicability;
    /** 0 至 10 的维度分；不适用时为空。 */
    private BigDecimal score;
    /** 评价发生时的原始权重。 */
    private BigDecimal weight;
    /** 确定性加权分；不适用时为空。 */
    private BigDecimal weightedScore;
    /** 面向用户的评分说明。 */
    private String scoreExplanation;
    /** 评分证据 JSON 快照。 */
    private String evidence;
    /** 维度评分创建时间。 */
    private LocalDateTime createdAt;
}
