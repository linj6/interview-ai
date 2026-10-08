package com.linj6.interviewagent.candidate.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** candidate 表映射对象。 */
@Getter
@Setter
@TableName("candidate")
public class CandidateDO {
    /** Candidate 标识。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 所属 User 标识。 */
    private Long userId;
    /** 候选人姓名。 */
    private String name;
}
