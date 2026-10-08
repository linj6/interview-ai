package com.linj6.interviewagent.candidate.api;
/** 当前候选人资料管理能力；写入参与调用方本地事务。 */
public interface CandidateProfileApi {
    CandidateManagedProfile getProfile(Long candidateId);

    void replaceProfile(Long candidateId, CandidateManagedProfile profile);
}
