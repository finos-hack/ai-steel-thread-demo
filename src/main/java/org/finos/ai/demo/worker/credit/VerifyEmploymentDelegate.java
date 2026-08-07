package org.finos.ai.demo.worker.credit;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-process alternative to {@link VerifyEmploymentWorker} for tools inside an agent ad-hoc
 * subprocess.
 */
@Component("verifyEmploymentDelegate")
public class VerifyEmploymentDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(VerifyEmploymentDelegate.class);

  private static final int MIN_ANNUAL_INCOME = 45_000;
  private static final int MAX_ANNUAL_INCOME = 120_000;

  @Override
  public void execute(DelegateExecution execution) {
    int annualIncome = ThreadLocalRandom.current().nextInt(MIN_ANNUAL_INCOME, MAX_ANNUAL_INCOME + 1);

    LOG.info(
        "Verified employment for execution {} in process instance {}: employmentVerified=true, annualIncome={}",
        execution.getId(), execution.getProcessInstanceId(), annualIncome);

    execution.setVariables(Map.of(
        VerifyEmploymentWorker.VAR_EMPLOYMENT_VERIFIED, true,
        VerifyEmploymentWorker.VAR_ANNUAL_INCOME, annualIncome));
  }

}
