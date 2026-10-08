package com.linj6.interviewagent.interview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** answer 表映射对象。 */
@Getter
@Setter
@TableName("answer")
public class AnswerDO {
    /** Answer 标识。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属 Interview 标识。 */
    private Long interviewId;
    /** 对应 Question 标识。 */
    private Long questionId;
    /** 候选人提交的不可变回答正文。 */
    private String content;
    /** 正式回答创建时间。 */
    private LocalDateTime createdAt;
}
