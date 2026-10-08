package com.linj6.interviewagent.interview.domain;

/** 可恢复业务执行阶段；枚举名直接映射数据库 VARCHAR 值。 */
public enum InterviewPhase {
    /** 正在生成并持久化首题。 */
    OPENING,
    /** 已存在正式问题，等待回答。 */
    WAITING_ANSWER,
    /** 已保存回答，正在完成本轮处理。 */
    PROCESSING_TURN
}
