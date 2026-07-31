package org.finos.fluxnova.worker.compliance;

import org.finos.fluxnova.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.finos.fluxnova.bpm.client.task.ExternalTask;
import org.finos.fluxnova.bpm.client.task.ExternalTaskHandler;
import org.finos.fluxnova.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = ComplianceCheckWorker.TOPIC)
public class ComplianceCheckWorker implements ExternalTaskHandler {

  public static final String TOPIC = "compliance-check";

  private static final Logger LOG = LoggerFactory.getLogger(ComplianceCheckWorker.class);

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    LOG.debug("Received external task {} for topic {}", externalTask.getId(), TOPIC);

    // TODO: read input variables, run compliance checks, and complete the task
    // externalTaskService.complete(externalTask, variables);
  }

}
