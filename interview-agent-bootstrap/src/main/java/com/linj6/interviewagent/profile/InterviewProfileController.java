package com.linj6.interviewagent.profile;

import com.linj6.interviewagent.common.PersonalInstanceIdentity;
import com.linj6.interviewagent.common.RequestIdContext;
import com.linj6.interviewagent.common.api.Result;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/** Current Interview Profile 聚合读写入口。 */
@RestController
@RequestMapping("/api/interview-profile")
public class InterviewProfileController {
    private static final Logger log = LoggerFactory.getLogger(InterviewProfileController.class);
    private final InterviewProfileApplicationService application;

    public InterviewProfileController(InterviewProfileApplicationService application) {
        this.application = application;
    }

    @GetMapping
    public Result<InterviewProfileResponse> getProfile() {
        return Result.success(application.getProfile());
    }

    @PutMapping
    public Result<InterviewProfileResponse> replaceProfile(@Valid @RequestBody InterviewProfileRequest request) {
        InterviewProfileResponse profile = application.replaceProfile(request);
        // Service 代理返回时事务已提交，日志只保留 identity 和数量，不记录个人资料原文。
        log.info(
                "Profile replacement committed requestId={} candidateId={} positionId={} projectCount={}",
                RequestIdContext.current(),
                PersonalInstanceIdentity.CANDIDATE_ID,
                PersonalInstanceIdentity.POSITION_ID,
                profile.projects().size());
        return Result.success(profile);
    }
}
