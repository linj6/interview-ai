package com.linj6.interviewagent.interview.domain;

/** Question 生命周期状态；枚举名直接映射数据库 VARCHAR 值。 */
public enum QuestionStatus {
    /** 问题已经创建但尚未展示。 */
    CREATED,
    /** 问题已展示，等待候选人回答。 */
    WAITING_ANSWER,
    /** 已形成正式 Answer 并完成评价。 */
    ANSWERED,
    /** 候选人跳过的问题。 */
    SKIPPED
}
