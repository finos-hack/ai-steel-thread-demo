package org.finos.ai.demo.worker.credit;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.finos.fluxnova.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.finos.fluxnova.bpm.client.task.ExternalTask;
import org.finos.fluxnova.bpm.client.task.ExternalTaskHandler;
import org.finos.fluxnova.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = VerifyEmploymentWorker.TOPIC)
public class VerifyEmploymentWorker implements ExternalTaskHandler {

  public static final String TOPIC = "verify-employment";

  public static final String VAR_EMPLOYMENT_VERIFIED = "employmentVerified";
  public static final String VAR_ANNUAL_INCOME = "annualIncome";

  private static final int MIN_ANNUAL_INCOME = 45_000;
  private static final int MAX_ANNUAL_INCOME = 120_000;

  private static final Logger LOG = LoggerFactory.getLogger(VerifyEmploymentWorker.class);

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    int annualIncome = ThreadLocalRandom.current().nextInt(MIN_ANNUAL_INCOME, MAX_ANNUAL_INCOME + 1);

    LOG.info(
        "Verified employment for external task {} in process instance {}: employmentVerified=true, annualIncome={}",
        externalTask.getId(), externalTask.getProcessInstanceId(), annualIncome);

    externalTaskService.complete(externalTask, Map.of(
        VAR_EMPLOYMENT_VERIFIED, true,
        VAR_ANNUAL_INCOME, annualIncome));
  }

}
