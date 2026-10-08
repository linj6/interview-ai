package com.linj6.interviewagent.interview.application;

import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.evaluation.api.evaluation.DimensionScoreDTO;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationApi;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationDTO;
import com.linj6.interviewagent.evaluation.api.evaluation.Feedback;
import com.linj6.interviewagent.evaluation.api.evaluation.MissingPoint;
import com.linj6.interviewagent.evaluation.api.evaluation.ReferenceAnswer;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.QuestionStatus;
import com.linj6.interviewagent.interview.domain.QuestionType;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionSnapshot;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse.DimensionScoreReview;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse.EvaluationReview;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse.FollowUpQuestionReview;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse.MainQuestionReview;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse.MissingPointReview;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse.Overview;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse.ReferenceAnswerReview;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse.Result;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 从已持久化的 Interview 业务事实动态组装完整复盘。 */
@Service
@RequiredArgsConstructor
public class InterviewReviewApplicationService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final EvaluationApi evaluationApi;

    public InterviewReviewResponse getReview(Long interviewId) {
        InterviewSnapshot interview = interviewRepository.findById(interviewId);
        if (interview.status() != InterviewStatus.COMPLETED) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
        if (interview.currentPhase() != null
                || interview.startedAt() == null
                || interview.completedAt() == null
                || interview.completedAt().isBefore(interview.startedAt())) {
            throw inconsistent();
        }

        List<QuestionSnapshot> questions = questionRepository.findByInterviewId(interviewId);
        List<AnswerSnapshot> answers = answerRepository.findByInterviewId(interviewId);
        List<EvaluationDTO> evaluations = evaluationApi.findByInterviewId(interviewId);
        Map<Long, AnswerSnapshot> answersByQuestion = uniqueIndex(answers, AnswerSnapshot::questionId);
        uniqueIndex(answers, AnswerSnapshot::id);
        Map<Long, EvaluationDTO> evaluationsByQuestion = uniqueIndex(evaluations, EvaluationDTO::questionId);
        Map<Long, QuestionSnapshot> questionsById = uniqueIndex(questions, QuestionSnapshot::id);

        List<QuestionSnapshot> mainQuestions = new ArrayList<>();
        Map<Long, List<QuestionSnapshot>> followUpsByParent = new HashMap<>();
        Set<Integer> sequenceNumbers = new HashSet<>();
        for (QuestionSnapshot question : questions) {
            if (question.interviewId() == null
                    || !question.interviewId().equals(interviewId)
                    || question.id() == null
                    || question.sequenceNo() == null
                    || question.content() == null
                    || question.type() == null
                    || question.status() == null
                    || !sequenceNumbers.add(question.sequenceNo())) {
                throw inconsistent();
            }
            if (question.type() == QuestionType.MAIN) {
                if (question.parentQuestionId() != null) {
                    throw inconsistent();
                }
                mainQuestions.add(question);
            } else if (question.type() == QuestionType.FOLLOW_UP) {
                QuestionSnapshot parent = questionsById.get(question.parentQuestionId());
                if (parent == null || parent.type() != QuestionType.MAIN || parent.parentQuestionId() != null) {
                    throw inconsistent();
                }
                followUpsByParent
                        .computeIfAbsent(parent.id(), ignored -> new ArrayList<>())
                        .add(question);
            } else {
                throw inconsistent();
            }
        }
        if (mainQuestions.isEmpty()) {
            throw inconsistent();
        }
        for (AnswerSnapshot answer : answers) {
            if (answer.id() == null
                    || answer.questionId() == null
                    || answer.interviewId() == null
                    || !Objects.equals(answer.interviewId(), interviewId)
                    || !questionsById.containsKey(answer.questionId())) {
                throw inconsistent();
            }
        }
        for (Long questionId : evaluationsByQuestion.keySet()) {
            EvaluationDTO evaluation = evaluationsByQuestion.get(questionId);
            if (!questionsById.containsKey(questionId) || !Objects.equals(evaluation.interviewId(), interviewId)) {
                throw inconsistent();
            }
        }

        int answered = 0;
        int skipped = 0;
        int unanswered = 0;
        for (QuestionSnapshot question : questions) {
            AnswerSnapshot answer = answersByQuestion.get(question.id());
            EvaluationDTO evaluation = evaluationsByQuestion.get(question.id());
            if (question.status() == QuestionStatus.ANSWERED) {
                if (answer == null || evaluation == null || !Objects.equals(evaluation.answerId(), answer.id())) {
                    throw inconsistent();
                }
                answered++;
            } else if (question.status() == QuestionStatus.SKIPPED) {
                if (answer != null || evaluation != null) {
                    throw inconsistent();
                }
                skipped++;
            } else if (question.status() == QuestionStatus.WAITING_ANSWER) {
                if (answer != null || evaluation != null) {
                    throw inconsistent();
                }
                unanswered++;
            } else {
                throw inconsistent();
            }
        }

        mainQuestions.sort(java.util.Comparator.comparing(QuestionSnapshot::sequenceNo));
        List<MainQuestionReview> responseQuestions = mainQuestions.stream()
                .map(main -> {
                    List<QuestionSnapshot> followUps = followUpsByParent.getOrDefault(main.id(), List.of());
                    return new MainQuestionReview(
                            main.id(),
                            main.content(),
                            result(main),
                            answerContent(main, answersByQuestion),
                            evaluationReview(main, evaluationsByQuestion),
                            followUps.stream()
                                    .sorted(java.util.Comparator.comparing(QuestionSnapshot::sequenceNo))
                                    .map(question -> new FollowUpQuestionReview(
                                            question.id(),
                                            question.content(),
                                            result(question),
                                            answerContent(question, answersByQuestion),
                                            evaluationReview(question, evaluationsByQuestion)))
                                    .toList());
                })
                .toList();
        return new InterviewReviewResponse(
                interviewId,
                new Overview(
                        interview.startedAt(),
                        interview.completedAt(),
                        Duration.between(interview.startedAt(), interview.completedAt())
                                .getSeconds(),
                        mainQuestions.size(),
                        questions.size() - mainQuestions.size(),
                        answered,
                        skipped,
                        unanswered),
                responseQuestions);
    }

    private Result result(QuestionSnapshot question) {
        return switch (question.status()) {
            case ANSWERED -> Result.ANSWERED;
            case SKIPPED -> Result.SKIPPED;
            case WAITING_ANSWER -> Result.UNANSWERED;
            default -> throw inconsistent();
        };
    }

    private String answerContent(QuestionSnapshot question, Map<Long, AnswerSnapshot> answers) {
        AnswerSnapshot answer = answers.get(question.id());
        return answer == null ? null : answer.content();
    }

    private EvaluationReview evaluationReview(QuestionSnapshot question, Map<Long, EvaluationDTO> evaluations) {
        EvaluationDTO evaluation = evaluations.get(question.id());
        if (evaluation == null) {
            return null;
        }
        return new EvaluationReview(
                evaluation.overallScore(),
                descriptions(evaluation.strengths()),
                descriptions(evaluation.weaknesses()),
                evaluation.missingPoints().stream().map(this::missingPoint).toList(),
                referenceAnswer(evaluation.referenceAnswer()),
                evaluation.dimensionScores().stream()
                        .filter(DimensionScoreDTO::applicable)
                        .map(score -> new DimensionScoreReview(score.name(), score.score(), score.explanation()))
                        .toList());
    }

    private List<String> descriptions(List<Feedback> feedback) {
        return feedback.stream().map(Feedback::description).toList();
    }

    private MissingPointReview missingPoint(MissingPoint point) {
        return new MissingPointReview(point.description(), point.importance(), point.explanation());
    }

    private ReferenceAnswerReview referenceAnswer(ReferenceAnswer answer) {
        return answer == null
                ? null
                : new ReferenceAnswerReview(answer.answerForm(), answer.keyPoints(), answer.content());
    }

    private <T> Map<Long, T> uniqueIndex(List<T> values, Function<T, Long> keyFunction) {
        Map<Long, T> result = new HashMap<>();
        for (T value : values) {
            Long key = keyFunction.apply(value);
            if (key == null || result.putIfAbsent(key, value) != null) {
                throw inconsistent();
            }
        }
        return result;
    }

    private BusinessException inconsistent() {
        return new BusinessException(InterviewErrorCode.INTERVIEW_DATA_INCONSISTENT);
    }
}
