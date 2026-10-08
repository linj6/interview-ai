package com.linj6.interviewagent.interview.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Interview Duration 配置，并在应用启动时验证其分钟数。 */
@Component
public class InterviewDurationSettings {

    private final long durationMinutes;

    public InterviewDurationSettings(@Value("${app.interview.duration-minutes:60}") long durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("app.interview.duration-minutes must be greater than 0");
        }
        this.durationMinutes = durationMinutes;
    }

    public long durationMinutes() {
        return durationMinutes;
    }
}
