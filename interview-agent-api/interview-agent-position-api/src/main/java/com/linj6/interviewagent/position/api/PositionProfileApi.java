package com.linj6.interviewagent.position.api;
/** 岗位资料写能力；参与调用方本地事务。 */
public interface PositionProfileApi {
    void replaceProfile(Long positionId, PositionInterviewProfile profile);
}
