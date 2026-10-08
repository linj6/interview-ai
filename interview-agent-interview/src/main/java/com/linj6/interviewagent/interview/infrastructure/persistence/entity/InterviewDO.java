package com.linj6.interviewagent.interview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linj6.interviewagent.interview.domain.InterviewMode;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.KnowledgeArea;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** interview 表映射对象。 */
@Getter
@Setter
@TableName("interview")
public class InterviewDO {
    /** Interview 标识。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 候选人标识。 */
    private Long candidateId;
    /** 目标岗位标识。 */
    private Long positionId;
    /** 面试模式。 */
    private InterviewMode mode = InterviewMode.COMPREHENSIVE;
    /** 仅技术专项提供的知识领域。 */
    private KnowledgeArea focusArea;
    /** Interview 生命周期状态。 */
    private InterviewStatus status;
    /** 可持久化、可恢复的业务执行阶段。 */
    private InterviewPhase currentPhase;
    /** 乐观锁版本。 */
    private Long version;
    /** 创建时间。 */
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
    /** Interview 的硬截止时间。 */
    private LocalDateTime expiresAt;
}
