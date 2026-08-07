package org.finos.ai.demo.worker.compliance;

import java.util.Map;

import org.finos.fluxnova.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.finos.fluxnova.bpm.client.task.ExternalTask;
import org.finos.fluxnova.bpm.client.task.ExternalTaskHandler;
import org.finos.fluxnova.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Advances the fraud-path subprocess to {@code UserTask_ComplianceReview}.
 *
 * <p>Real compliance screening is left to the human task; this worker only unblocks the wait
 * state so Launch UI / Cockpit can claim and complete the review.
 */
@Component
@ExternalTaskSubscription(topicName = ComplianceCheckWorker.TOPIC)
public class ComplianceCheckWorker implements ExternalTaskHandler {

  public static final String TOPIC = "compliance-check";

  public static final String VAR_COMPLIANCE_CHECK_STATUS = "complianceCheckStatus";

  private static final Logger LOG = LoggerFactory.getLogger(ComplianceCheckWorker.class);

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    LOG.info(
        "Completed compliance-check stub for external task {} in process instance {} — awaiting human review",
        externalTask.getId(),
        externalTask.getProcessInstanceId());

    externalTaskService.complete(
        externalTask, Map.of(VAR_COMPLIANCE_CHECK_STATUS, "PENDING_HUMAN_REVIEW"));
  }
}
