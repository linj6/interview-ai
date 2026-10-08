package com.linj6.interviewagent.interview.domain;

/** V1 InterviewMode，枚举名作为持久化值。 */
public enum InterviewMode {
    /** 综合面试。 */
    COMPREHENSIVE,
    /** 项目深挖。 */
    PROJECT_DEEP_DIVE,
    /** 技术专项。 */
    TECHNICAL_FOCUS;

    /** 仅技术专项要求 focusArea，其余模式禁止提供。 */
    public void validateFocusArea(KnowledgeArea focusArea) {
        if ((this == TECHNICAL_FOCUS) != (focusArea != null)) {
            throw new IllegalArgumentException("focusArea must be provided only for TECHNICAL_FOCUS");
        }
    }
}
