package com.linj6.interviewagent.interview.domain;

/** Question 类型：主问题或追问。 */
public enum QuestionType {
    /** 面试主问题。 */
    MAIN,
    /** 基于父问题继续深入的追问。 */
    FOLLOW_UP
}
