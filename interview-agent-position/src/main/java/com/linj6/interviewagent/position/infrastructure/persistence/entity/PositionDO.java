package com.linj6.interviewagent.position.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** position 表映射对象。 */
@Getter
@Setter
@TableName("position")
public class PositionDO {
    /** Position 标识。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 岗位标题。 */
    private String title;
    /** 岗位职责与要求说明。 */
    private String description;
}
