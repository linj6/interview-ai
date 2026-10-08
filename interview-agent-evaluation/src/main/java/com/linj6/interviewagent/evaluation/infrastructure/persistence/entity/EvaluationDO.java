package com.linj6.interviewagent.evaluation.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** evaluation 表映射对象。 */
@Getter
@Setter
@TableName("evaluation")
public class EvaluationDO {
    /** Evaluation 标识。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属 Interview 标识。 */
    private Long interviewId;
    /** 对应 Question 标识。 */
    private Long questionId;
    /** 对应不可变 Answer 标识。 */
    private Long answerId;
    /** Question 绑定的 Evaluation Profile 标识。 */
    private Long profileId;
    /** Java 确定性计算的十分制总分。 */
    @TableField("overall_score_10")
    private BigDecimal overallScore10;
    /** 由十分制总分换算的百分制总分。 */
    @TableField("overall_score_100")
    private BigDecimal overallScore100;
    /** 优势反馈 JSON 快照。 */
    private String strengths;
    /** 不足反馈 JSON 快照。 */
    private String weaknesses;
    /** 缺失点 JSON 快照。 */
    private String missingPoints;
    /** 参考答案 JSON 快照。 */
    private String referenceAnswer;
    /** Prompt 轻量版本。 */
    private String promptVersion;
    /** Evaluator 轻量版本。 */
    private String evaluatorVersion;
    /** 模型名称。 */
    private String model;
    /** 模型输入 Token 数。 */
    private Integer inputTokens;
    /** 模型输出 Token 数。 */
    private Integer outputTokens;
    /** 模型总 Token 数。 */
    private Integer totalTokens;
    /** Evaluator 调用耗时，单位毫秒。 */
    private Long latencyMs;
    /** 正式 Evaluation 创建时间。 */
    private LocalDateTime createdAt;
}
