package com.linj6.interviewagent.interview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linj6.interviewagent.interview.domain.QuestionStatus;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** question 表映射对象。 */
@Getter
@Setter
@TableName("question")
public class QuestionDO {
    /** Question 标识。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属 Interview 标识。 */
    private Long interviewId;
    /** 正式 PlanItem 标识；MAIN 关联真实计划项，FOLLOW_UP 继承所属 MAIN。 */
    private Long planItemId;
    /** Interview 内展示顺序。 */
    private Integer sequenceNo;
    /** 父问题标识；主问题为空。 */
    private Long parentQuestionId;
    /** MAIN 或 FOLLOW_UP。 */
    private String type;
    /** Question 生命周期状态。 */
    private QuestionStatus status;
    /** 问题正文。 */
    @TableField("question_content")
    private String content;
    /** 可选 Topic 标识。 */
    private Long topicId;
    /** 固定绑定的 Evaluation Profile 标识。 */
    private Long evaluationProfileId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
