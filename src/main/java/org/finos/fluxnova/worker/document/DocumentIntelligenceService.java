package org.finos.fluxnova.worker.document;

import org.finos.fluxnova.observability.LlmTokenUsageMetrics;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

@Service
public class DocumentIntelligenceService {

  private static final String EXTRACTION_PROMPT = """
      Extract identity document fields from the attached image.
      Return JSON with these properties only:
      firstName, lastName, dateOfBirth, expirationDate, issueDate, address, sex, idNumber, idType.
      Use ISO dates (YYYY-MM-DD) when a date is visible.
      Use null for fields that are not visible or cannot be read confidently.
      idType should describe the document (for example PASSPORT, DRIVERS_LICENSE, NATIONAL_ID).
      """;

  private final ChatClient chatClient;
  private final LlmTokenUsageMetrics tokenUsageMetrics;
  private final String model;
  private final String workflow;
  private final String task;
  private final String provider;

  public DocumentIntelligenceService(
      ChatModel chatModel,
      LlmTokenUsageMetrics tokenUsageMetrics,
      @Value("${fluxnova.document-intelligence.model:gpt-5.4}") String model,
      @Value("${fluxnova.document-intelligence.workflow:LoanApprovalProcess_AI}") String workflow,
      @Value("${fluxnova.document-intelligence.telemetry-task:" + ExtractDataFromDocumentWorker.TOPIC + "}")
          String task,
      @Value("${fluxnova.document-intelligence.provider:openai}") String provider) {
    this.tokenUsageMetrics = tokenUsageMetrics;
    this.model = model;
    this.workflow = workflow;
    this.task = task;
    this.provider = provider;
    this.chatClient = ChatClient.builder(chatModel)
        .defaultOptions(OpenAiChatOptions.builder()
            .model(model)
            .temperature(1.0))
        .build();
  }

  IdentityDocumentData extract(byte[] imageBytes, MimeType mimeType) {
    Media media = Media.builder()
        .mimeType(mimeType)
        .data(new ByteArrayResource(imageBytes))
        .build();

    ResponseEntity<ChatResponse, IdentityDocumentData> result =
        chatClient.prompt()
            .user(user -> user.text(EXTRACTION_PROMPT).media(media))
            .call()
            .responseEntity(IdentityDocumentData.class);

    tokenUsageMetrics.recordFromChatResponse(
        workflow, task, model, provider, result.response());

    return result.entity();
  }

  static MimeType resolveImageMimeType(String mimeType, String filename) {
    if (mimeType != null && !mimeType.isBlank()) {
      return MimeTypeUtils.parseMimeType(mimeType);
    }
    if (filename != null) {
      String lower = filename.toLowerCase();
      if (lower.endsWith(".png")) {
        return MimeTypeUtils.IMAGE_PNG;
      }
      if (lower.endsWith(".webp")) {
        return MimeTypeUtils.parseMimeType("image/webp");
      }
    }
    return MimeTypeUtils.IMAGE_JPEG;
  }

}
