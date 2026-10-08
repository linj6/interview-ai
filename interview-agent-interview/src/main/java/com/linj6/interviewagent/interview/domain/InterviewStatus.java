package com.linj6.interviewagent.interview.domain;

/** Interview 生命周期状态；枚举名直接映射数据库 VARCHAR 值。 */
public enum InterviewStatus {
    /** 尚未启动。 */
    CREATED,
    /** 面试正在进行。 */
    IN_PROGRESS,
    /** 在稳定边界暂停。 */
    PAUSED,
    /** 面试已结束，不能再流转。 */
    COMPLETED
}
