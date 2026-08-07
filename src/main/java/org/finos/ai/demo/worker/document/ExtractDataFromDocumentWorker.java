package org.finos.ai.demo.worker.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.finos.fluxnova.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.finos.fluxnova.bpm.client.task.ExternalTask;
import org.finos.fluxnova.bpm.client.task.ExternalTaskHandler;
import org.finos.fluxnova.bpm.client.task.ExternalTaskService;
import org.finos.fluxnova.bpm.engine.variable.value.FileValue;
import org.finos.fluxnova.bpm.engine.variable.value.TypedValue;
import org.finos.ai.demo.observability.LlmCallMetadataVariables;
import org.finos.ai.demo.observability.LlmTokenUsageVariables;
import org.finos.ai.demo.tokenomics.TokenomicsCostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

@Component
@ExternalTaskSubscription(
    topicName = ExtractDataFromDocumentWorker.TOPIC,
    variableNames = {
      ExtractDataFromDocumentWorker.VAR_ATTACHED_FILE,
      ExtractDataFromDocumentWorker.VAR_DOCUMENT_PROCESSING_AGENT_PROVIDER,
      ExtractDataFromDocumentWorker.VAR_DOCUMENT_PROCESSING_AGENT_MODEL,
      ExtractDataFromDocumentWorker.VAR_DOCUMENT_PROCESSING_AGENT_CACHE_STRATEGY,
      ExtractDataFromDocumentWorker.VAR_DOCUMENT_PROCESSING_AGENT_PROMPT_CACHE_KEY
    })
public class ExtractDataFromDocumentWorker implements ExternalTaskHandler {

  public static final String TOPIC = "document-intelligence";

  public static final String VAR_ATTACHED_FILE = "attachedFile";
  public static final String VAR_DOCUMENT_PROCESSING_AGENT_PROVIDER =
      "documentProcessingAgentProvider";
  public static final String VAR_DOCUMENT_PROCESSING_AGENT_MODEL =
      "documentProcessingAgentModel";
  public static final String VAR_DOCUMENT_PROCESSING_AGENT_CACHE_STRATEGY =
      "documentProcessingAgentCacheStrategy";
  public static final String VAR_DOCUMENT_PROCESSING_AGENT_PROMPT_CACHE_KEY =
      "documentProcessingAgentPromptCacheKey";
  public static final String VAR_IS_INVALID = "isInvalid";
  public static final String VAR_FIRST_NAME = "firstName";
  public static final String VAR_LAST_NAME = "lastName";
  public static final String VAR_DATE_OF_BIRTH = "dateOfBirth";
  public static final String VAR_EXPIRATION_DATE = "expirationDate";
  public static final String VAR_ISSUE_DATE = "issueDate";
  public static final String VAR_ADDRESS = "address";
  public static final String VAR_SEX = "sex";
  public static final String VAR_ID_NUMBER = "idNumber";
  public static final String VAR_ID_TYPE = "idType";

  private static final Logger LOG = LoggerFactory.getLogger(ExtractDataFromDocumentWorker.class);

  private final DocumentIntelligenceService documentIntelligenceService;
  private final TokenomicsCostService tokenomicsCostService;
  private final String attachedFileVariable;

  public ExtractDataFromDocumentWorker(
      DocumentIntelligenceService documentIntelligenceService,
      TokenomicsCostService tokenomicsCostService,
      @Value("${fluxnova.document-intelligence.attached-file-variable:" + VAR_ATTACHED_FILE + "}")
          String attachedFileVariable) {
    this.documentIntelligenceService = documentIntelligenceService;
    this.tokenomicsCostService = tokenomicsCostService;
    this.attachedFileVariable = attachedFileVariable;
  }

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    try {
      byte[] imageBytes = readAttachedImage(externalTask);
      MimeType mimeType = resolveMimeType(externalTask, imageBytes);
      String provider = asString(externalTask.getVariable(VAR_DOCUMENT_PROCESSING_AGENT_PROVIDER));
      String model = asString(externalTask.getVariable(VAR_DOCUMENT_PROCESSING_AGENT_MODEL));
      String cacheStrategy =
          asString(externalTask.getVariable(VAR_DOCUMENT_PROCESSING_AGENT_CACHE_STRATEGY));
      String promptCacheKey =
          asString(externalTask.getVariable(VAR_DOCUMENT_PROCESSING_AGENT_PROMPT_CACHE_KEY));
      DocumentIntelligenceService.ExtractionResult extraction =
          documentIntelligenceService.extract(
              imageBytes, mimeType, provider, model, cacheStrategy, promptCacheKey);
      IdentityDocumentData extracted = extraction.data();
      boolean isInvalid = extracted == null || extracted.isSubstantivelyEmpty();
      Map<String, Object> variables = toProcessVariables(extracted, isInvalid);
      variables.putAll(LlmTokenUsageVariables.toVariables(extraction.tokenUsage()));
      if (externalTask.getActivityId() != null) {
        variables.putAll(
            LlmTokenUsageVariables.toScopedVariables(
                externalTask.getActivityId(), extraction.tokenUsage()));
        Double estimatedCost =
            tokenomicsCostService.estimateUsd(
                extraction.provider(), extraction.model(), extraction.tokenUsage());
        if (estimatedCost != null) {
          variables.put(LlmTokenUsageVariables.ESTIMATED_COST_USD, estimatedCost);
          variables.putAll(
              LlmTokenUsageVariables.toScopedCostVariables(
                  externalTask.getActivityId(), estimatedCost));
        }
        // External-task workers have no BPMN execution context, so mi-4 call metadata must be
        // written on complete() (ProcessScopedLlmTokenUsageWriter cannot see this thread).
        variables.putAll(
            LlmCallMetadataVariables.toScopedVariables(
                externalTask.getActivityId(),
                extraction.model(),
                extraction.provider(),
                extraction.modelVersion(),
                extraction.durationMs(),
                extraction.status(),
                extraction.contextKeys(),
                extraction.errorType()));
      }

      LOG.info(
          "Extracted identity document data for external task {} in process instance {}: "
              + "idType={}, isInvalid={}, tokens={} (PII fields omitted from logs — mi-1)",
          externalTask.getId(),
          externalTask.getProcessInstanceId(),
          variables.get(VAR_ID_TYPE),
          isInvalid,
          extraction.tokenUsage());

      externalTaskService.complete(externalTask, variables);
    } catch (RuntimeException exception) {
      LOG.warn(
          "Document extraction failed for external task {} in process instance {}",
          externalTask.getId(),
          externalTask.getProcessInstanceId(),
          exception);
      externalTaskService.complete(externalTask, Map.of(VAR_IS_INVALID, true));
    }
  }

  private byte[] readAttachedImage(ExternalTask externalTask) {
    TypedValue typedValue = externalTask.getVariableTyped(attachedFileVariable);
    if (typedValue == null) {
      throw new IllegalArgumentException(
          "Process variable '"
              + attachedFileVariable
              + "' is missing. Start via POST /api/demo/loan-approval/start-with-sample-id "
              + "(Modeler start JSON cannot upload a file value).");
    }
    if (!(typedValue instanceof FileValue fileValue)) {
      throw new IllegalArgumentException(
          "Process variable '"
              + attachedFileVariable
              + "' must be a File value, but was "
              + typedValue.getType()
              + ". Start via POST /api/demo/loan-approval/start-with-sample-id.");
    }
    try (InputStream inputStream = fileValue.getValue()) {
      return inputStream.readAllBytes();
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to read attached file '" + attachedFileVariable + "'", exception);
    }
  }

  private MimeType resolveMimeType(ExternalTask externalTask, byte[] imageBytes) {
    FileValue fileValue = externalTask.getVariableTyped(attachedFileVariable);
    return DocumentIntelligenceService.resolveImageMimeType(
        fileValue.getMimeType(), fileValue.getFilename(), imageBytes);
  }

  static Map<String, Object> toProcessVariables(IdentityDocumentData extracted, boolean isInvalid) {
    Map<String, Object> variables = new HashMap<>();
    variables.put(VAR_IS_INVALID, isInvalid);
    if (extracted == null) {
      return variables;
    }
    putIfPresent(variables, VAR_FIRST_NAME, extracted.firstName());
    putIfPresent(variables, VAR_LAST_NAME, extracted.lastName());
    putIfPresent(variables, VAR_DATE_OF_BIRTH, extracted.dateOfBirth());
    putIfPresent(variables, VAR_EXPIRATION_DATE, extracted.expirationDate());
    putIfPresent(variables, VAR_ISSUE_DATE, extracted.issueDate());
    putIfPresent(variables, VAR_ADDRESS, extracted.address());
    putIfPresent(variables, VAR_SEX, extracted.sex());
    putIfPresent(variables, VAR_ID_NUMBER, extracted.idNumber());
    putIfPresent(variables, VAR_ID_TYPE, extracted.idType());
    return variables;
  }

  private static void putIfPresent(Map<String, Object> variables, String name, String value) {
    if (value != null && !value.isBlank()) {
      variables.put(name, value);
    }
  }

  private static String asString(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

}
