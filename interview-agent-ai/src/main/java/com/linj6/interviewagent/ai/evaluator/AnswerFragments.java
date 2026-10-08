package com.linj6.interviewagent.ai.evaluator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public final class AnswerFragments {
    private AnswerFragments() {}

    public static List<AnswerFragment> from(String answer) {
        String[] lines = answer == null ? new String[0] : answer.split("\\R", -1);
        List<String> nonEmptyLines = Arrays.stream(lines)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
        return IntStream.range(0, nonEmptyLines.size())
                .mapToObj(index -> new AnswerFragment("E" + (index + 1), nonEmptyLines.get(index)))
                .toList();
    }
}
