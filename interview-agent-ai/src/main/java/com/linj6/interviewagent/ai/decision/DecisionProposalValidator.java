package com.linj6.interviewagent.ai.decision;

import com.linj6.interviewagent.ai.api.decision.DecisionAction;
import com.linj6.interviewagent.ai.api.decision.DecisionContext;
import com.linj6.interviewagent.ai.api.decision.ValidatedDecisionResult;

public class DecisionProposalValidator {
    public ValidatedDecisionResult validate(
            DecisionContext context, DecisionProposal proposal, DecisionTargets targets) {
        if (context == null
                || context.allowedActions() == null
                || context.allowedActions().isEmpty()
                || proposal == null
                || proposal.action() == null) {
            throw new DecisionValidationException("Decision action is missing");
        }
        if (!context.allowedActions().contains(proposal.action())) {
            throw new DecisionValidationException("Decision action is not allowed");
        }
        if (proposal.reason() == null || proposal.reason().isBlank()) {
            throw new DecisionValidationException("Decision reason is blank");
        }
        if (proposal.action() != DecisionAction.NEXT_QUESTION
                && proposal.planItemRef() != null
                && !proposal.planItemRef().isBlank()) {
            throw new DecisionValidationException("Only NEXT_QUESTION may provide planItemRef");
        }
        if (proposal.action() == DecisionAction.END) {
            if (proposal.targetRef() != null && !proposal.targetRef().isBlank()) {
                throw new DecisionValidationException("END targetRef must be blank");
            }
            return new ValidatedDecisionResult(
                    proposal.action(), proposal.reason().trim(), null);
        }
        if (proposal.action() == DecisionAction.FOLLOW_UP) {
            String ref = proposal.targetRef();
            if (targets == null || targets.values().isEmpty()) {
                throw new DecisionValidationException("FOLLOW_UP is not allowed without missing points");
            }
            if (ref == null || !ref.matches("M[1-9][0-9]*")) {
                throw new DecisionValidationException("FOLLOW_UP targetRef is invalid");
            }
            String description = targets == null ? null : targets.resolve(ref);
            if (description == null) {
                throw new DecisionValidationException("FOLLOW_UP targetRef does not exist");
            }
            return new ValidatedDecisionResult(
                    proposal.action(), proposal.reason().trim(), description);
        }
        if (proposal.targetRef() != null && !proposal.targetRef().isBlank()) {
            throw new DecisionValidationException("NEXT_QUESTION targetRef must be blank");
        }
        String ref = proposal.planItemRef();
        Long id = ref != null && ref.matches("P[1-9][0-9]*") && targets != null ? targets.resolvePlanItem(ref) : null;
        if (id == null) throw new DecisionValidationException("NEXT_QUESTION planItemRef is invalid or unavailable");
        return new ValidatedDecisionResult(proposal.action(), proposal.reason().trim(), null, id);
    }
}
