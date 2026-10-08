package com.linj6.interviewagent.ai.evaluator;

/** A non-empty trimmed Answer line exposed to the evaluator under a stable reference. */
public record AnswerFragment(String evidenceRef, String text) {}
