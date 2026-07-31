package org.finos.fluxnova.demo;

import static org.finos.fluxnova.bpm.engine.variable.Variables.fileValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.variable.VariableMap;
import org.finos.fluxnova.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanApprovalDemoService {

  private static final int MIN_REQUESTED_AMOUNT = 10_000;
  private static final int MAX_REQUESTED_AMOUNT = 100_000;
  private static final String[] LOAN_PURPOSES = {
    "Personal",
    "Debt Consolidation",
    "Home Renovation",
    "Education",
    "Emergency",
    "Travel",
    "Medical Emergency",
    "Car"
  };

  private final RuntimeService runtimeService;
  private final RepositoryService repositoryService;
  private final String processDefinitionKey;
  private final String attachedFileVariable;
  private final Resource[] sampleIdCards;

  public LoanApprovalDemoService(
      RuntimeService runtimeService,
      RepositoryService repositoryService,
      ResourcePatternResolver resourcePatternResolver,
      @Value("${fluxnova.loan-approval-demo.process-definition-key:LoanApprovalProcess_AI}")
          String processDefinitionKey,
      @Value("${fluxnova.document-intelligence.attached-file-variable:attachedFile}")
          String attachedFileVariable,
      @Value(
              "${fluxnova.loan-approval-demo.sample-id-cards-pattern:classpath:sample/sample-id-card*.png}")
          String sampleIdCardsPattern) {
    this.runtimeService = runtimeService;
    this.repositoryService = repositoryService;
    this.processDefinitionKey = processDefinitionKey;
    this.attachedFileVariable = attachedFileVariable;
    this.sampleIdCards = loadSampleIdCards(resourcePatternResolver, sampleIdCardsPattern);
  }

  public StartLoanApprovalResponse startWithSampleIdCard(
      String businessKey, Integer requestedAmount, String loanPurpose) {
    ensureProcessDeployed();

    String resolvedBusinessKey =
        businessKey == null || businessKey.isBlank()
            ? "loan-demo-" + Instant.now().toEpochMilli()
            : businessKey;

    Resource sampleIdCard = pickRandomSampleIdCard();
    int resolvedRequestedAmount = resolveRequestedAmount(requestedAmount);
    String resolvedLoanPurpose = resolveLoanPurpose(loanPurpose);

    VariableMap variables =
        Variables.createVariables()
            .putValue(
                attachedFileVariable,
                fileValue(sampleIdCard.getFilename())
                    .file(readSampleIdCard(sampleIdCard))
                    .mimeType("image/png")
                    .create())
            .putValue("requestedAmount", resolvedRequestedAmount)
            .putValue("loanPurpose", resolvedLoanPurpose);

    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(
            processDefinitionKey, resolvedBusinessKey, variables);

    return new StartLoanApprovalResponse(
        processInstance.getProcessInstanceId(),
        resolvedBusinessKey,
        processDefinitionKey,
        attachedFileVariable,
        sampleIdCard.getFilename(),
        resolvedRequestedAmount,
        resolvedLoanPurpose);
  }

  private int resolveRequestedAmount(Integer requestedAmount) {
    if (requestedAmount == null) {
      return pickRandomRequestedAmount();
    }
    if (requestedAmount < MIN_REQUESTED_AMOUNT || requestedAmount > MAX_REQUESTED_AMOUNT) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "requestedAmount must be between "
              + MIN_REQUESTED_AMOUNT
              + " and "
              + MAX_REQUESTED_AMOUNT);
    }
    return requestedAmount;
  }

  private String resolveLoanPurpose(String loanPurpose) {
    if (loanPurpose == null || loanPurpose.isBlank()) {
      return pickRandomLoanPurpose();
    }
    for (String allowedPurpose : LOAN_PURPOSES) {
      if (allowedPurpose.equalsIgnoreCase(loanPurpose)) {
        return allowedPurpose;
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "loanPurpose must be one of: " + String.join(", ", LOAN_PURPOSES));
  }

  private int pickRandomRequestedAmount() {
    return ThreadLocalRandom.current()
        .nextInt(MIN_REQUESTED_AMOUNT, MAX_REQUESTED_AMOUNT + 1);
  }

  private String pickRandomLoanPurpose() {
    return LOAN_PURPOSES[ThreadLocalRandom.current().nextInt(LOAN_PURPOSES.length)];
  }

  private Resource pickRandomSampleIdCard() {
    return sampleIdCards[ThreadLocalRandom.current().nextInt(sampleIdCards.length)];
  }

  private void ensureProcessDeployed() {
    long deployedDefinitions =
        repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionKey(processDefinitionKey)
            .count();
    if (deployedDefinitions == 0) {
      throw new ResponseStatusException(
          HttpStatus.PRECONDITION_FAILED,
          "Process definition '"
              + processDefinitionKey
              + "' is not deployed. Deploy it from Fluxnova Modeler first.");
    }
  }

  private static Resource[] loadSampleIdCards(
      ResourcePatternResolver resourcePatternResolver, String pattern) {
    try {
      Resource[] resources = resourcePatternResolver.getResources(pattern);
      if (resources.length == 0) {
        throw new IllegalStateException("No sample ID cards found matching " + pattern);
      }
      Arrays.sort(resources, (left, right) -> {
        String leftName = left.getFilename() == null ? "" : left.getFilename();
        String rightName = right.getFilename() == null ? "" : right.getFilename();
        return leftName.compareTo(rightName);
      });
      return resources;
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to load sample ID cards from " + pattern, exception);
    }
  }

  private InputStream readSampleIdCard(Resource sampleIdCard) {
    try {
      if (!sampleIdCard.exists()) {
        throw new IllegalStateException(
            "Sample ID card not found at " + sampleIdCard.getDescription());
      }
      return sampleIdCard.getInputStream();
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to read sample ID card", exception);
    }
  }

}
