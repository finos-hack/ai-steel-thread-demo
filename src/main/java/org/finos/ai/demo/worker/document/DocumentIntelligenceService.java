package org.finos.ai.demo.worker.document;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.finos.fluxnova.bpm.engine.ai.agent.llm.provider.AgentProviderRegistry;
import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.finos.ai.demo.dlp.Mi1DataLeakagePrevention;
import org.finos.ai.demo.observability.LlmTokenUsageMetrics;
import org.finos.ai.demo.observability.SpringAiTokenUsageExtractor;
import org.finos.ai.demo.tokenomics.TokenomicsService;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
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

  private final AgentProviderRegistry providerRegistry;
  private final LlmTokenUsageMetrics tokenUsageMetrics;
  private final Mi1DataLeakagePrevention dataLeakagePrevention;
  private final ObjectProvider<TokenomicsService> tokenomicsService;
  private final String defaultModel;
  private final String workflow;
  private final String task;
  private final String defaultProvider;

  public DocumentIntelligenceService(
      AgentProviderRegistry providerRegistry,
      LlmTokenUsageMetrics tokenUsageMetrics,
      Mi1DataLeakagePrevention dataLeakagePrevention,
      ObjectProvider<TokenomicsService> tokenomicsService,
      @Value("${fluxnova.document-intelligence.model:gpt-5.4}") String model,
      @Value("${fluxnova.document-intelligence.workflow:LoanApprovalProcess_AI}") String workflow,
      @Value("${fluxnova.document-intelligence.telemetry-task:" + ExtractDataFromDocumentWorker.TOPIC + "}")
          String task,
      @Value("${fluxnova.document-intelligence.provider:openai}") String provider) {
    this.providerRegistry = providerRegistry;
    this.tokenUsageMetrics = tokenUsageMetrics;
    this.dataLeakagePrevention = dataLeakagePrevention;
    this.tokenomicsService = tokenomicsService;
    this.defaultModel = model;
    this.workflow = workflow;
    this.task = task;
    this.defaultProvider = provider;
  }

  public record ExtractionResult(
      IdentityDocumentData data,
      TokenUsage tokenUsage,
      String model,
      String provider,
      String modelVersion,
      long durationMs,
      String status,
      List<String> contextKeys,
      String errorType) {}

  ExtractionResult extract(byte[] imageBytes, MimeType mimeType) {
    return extract(imageBytes, mimeType, null, null, null, null);
  }

  ExtractionResult extract(
      byte[] imageBytes, MimeType mimeType, String providerOverride, String modelOverride) {
    return extract(imageBytes, mimeType, providerOverride, modelOverride, null, null);
  }

  ExtractionResult extract(
      byte[] imageBytes,
      MimeType mimeType,
      String providerOverride,
      String modelOverride,
      String cacheStrategyOverride,
      String promptCacheKeyOverride) {
    String provider = resolve(providerOverride, defaultProvider);
    String model = resolve(modelOverride, defaultModel);
    String cacheStrategy = blankToNull(cacheStrategyOverride);
    String promptCacheKey = blankToNull(promptCacheKeyOverride);

    dataLeakagePrevention.auditDocumentIntelligenceEgress(imageBytes == null ? 0 : imageBytes.length);
    TokenomicsService tokenomics = tokenomicsService.getIfAvailable();
    if (tokenomics != null) {
      tokenomics.enforceHardBudget(workflow);
    }

    Media media = Media.builder()
        .mimeType(mimeType)
        .data(new ByteArrayResource(imageBytes))
        .build();

    List<String> contextKeys = List.of("identity-document-image");
    long started = System.nanoTime();
    try {
      ChatModel chatModel = providerRegistry.get(provider);
      ChatClient chatClient = ChatClient.builder(chatModel).build();
      ChatClient.ChatClientRequestSpec spec =
          chatClient
              .prompt()
              .system(EXTRACTION_PROMPT)
              .user(user -> user.text("Extract fields from the attached identity document.").media(media));
      ChatOptions.Builder<?> options =
          buildRequestOptions(provider, model, cacheStrategy, promptCacheKey);
      if (options != null) {
        spec = spec.options(options);
      }
      ResponseEntity<ChatResponse, IdentityDocumentData> result =
          spec.call().responseEntity(IdentityDocumentData.class);

      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      TokenUsage tokenUsage = SpringAiTokenUsageExtractor.from(result.response());
      tokenUsageMetrics.recordCall(
          workflow,
          task,
          model,
          provider,
          model,
          durationMs,
          tokenUsage,
          true,
          null,
          contextKeys);
      return new ExtractionResult(
          result.entity(),
          tokenUsage,
          model,
          provider,
          model,
          durationMs,
          "success",
          contextKeys,
          null);
    } catch (RuntimeException exception) {
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      tokenUsageMetrics.recordCall(
          workflow,
          task,
          model,
          provider,
          model,
          durationMs,
          null,
          false,
          exception.getClass().getSimpleName(),
          contextKeys);
      throw exception;
    }
  }

  static ChatOptions.Builder<?> buildRequestOptions(
      String provider, String model, String cacheStrategy, String promptCacheKey) {
    String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    String resolvedModel = blankToNull(model);
    String resolvedCacheStrategy = blankToNull(cacheStrategy);
    String resolvedPromptCacheKey = blankToNull(promptCacheKey);

    if ("anthropic".equals(normalizedProvider)) {
      if (resolvedModel == null && resolvedCacheStrategy == null) {
        return null;
      }
      AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder().temperature(1.0);
      if (resolvedModel != null) {
        builder.model(resolvedModel);
      }
      if (resolvedCacheStrategy != null) {
        AnthropicCacheStrategy strategy =
            AnthropicCacheStrategy.valueOf(resolvedCacheStrategy.toUpperCase(Locale.ROOT));
        AnthropicCacheOptions.Builder cacheOptions =
            AnthropicCacheOptions.builder().strategy(strategy);
        if (strategy == AnthropicCacheStrategy.CONVERSATION_HISTORY) {
          cacheOptions.cacheToolResults(true);
        }
        builder.cacheOptions(cacheOptions.build());
      }
      return builder;
    }

    if ("openai".equals(normalizedProvider)) {
      if (resolvedModel == null && resolvedPromptCacheKey == null) {
        return null;
      }
      OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().temperature(1.0);
      if (resolvedModel != null) {
        builder.model(resolvedModel);
      }
      if (resolvedPromptCacheKey != null) {
        builder.promptCacheKey(resolvedPromptCacheKey);
      }
      return builder;
    }

    if (resolvedModel == null) {
      return null;
    }
    return ChatOptions.builder().model(resolvedModel).temperature(1.0);
  }

  private static String resolve(String override, String fallback) {
    if (override == null || override.isBlank()) {
      return fallback;
    }
    return override.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public static MimeType resolveImageMimeType(String mimeType, String filename) {
    return resolveImageMimeType(mimeType, filename, null);
  }

  /**
   * Prefer content sniffing when bytes are available — filenames and declared MIME types can be
   * wrong (e.g. a JPEG saved as {@code .png}). Anthropic rejects that mismatch; OpenAI is lenient.
   */
  public static MimeType resolveImageMimeType(String mimeType, String filename, byte[] imageBytes) {
    MimeType sniffed = detectImageMimeType(imageBytes);
    if (sniffed != null) {
      return sniffed;
    }
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
      if (lower.endsWith(".gif")) {
        return MimeTypeUtils.parseMimeType("image/gif");
      }
    }
    return MimeTypeUtils.IMAGE_JPEG;
  }

  /** Returns the image MIME type from magic bytes, or {@code null} if unrecognized. */
  public static MimeType detectImageMimeType(byte[] imageBytes) {
    if (imageBytes == null || imageBytes.length < 12) {
      return null;
    }
    // PNG: 89 50 4E 47 0D 0A 1A 0A
    if (imageBytes[0] == (byte) 0x89
        && imageBytes[1] == 0x50
        && imageBytes[2] == 0x4E
        && imageBytes[3] == 0x47) {
      return MimeTypeUtils.IMAGE_PNG;
    }
    // JPEG: FF D8 FF
    if (imageBytes[0] == (byte) 0xFF
        && imageBytes[1] == (byte) 0xD8
        && imageBytes[2] == (byte) 0xFF) {
      return MimeTypeUtils.IMAGE_JPEG;
    }
    // GIF: GIF87a / GIF89a
    if (imageBytes[0] == 'G'
        && imageBytes[1] == 'I'
        && imageBytes[2] == 'F'
        && imageBytes[3] == '8') {
      return MimeTypeUtils.parseMimeType("image/gif");
    }
    // WEBP: RIFF....WEBP
    if (imageBytes[0] == 'R'
        && imageBytes[1] == 'I'
        && imageBytes[2] == 'F'
        && imageBytes[3] == 'F'
        && imageBytes[8] == 'W'
        && imageBytes[9] == 'E'
        && imageBytes[10] == 'B'
        && imageBytes[11] == 'P') {
      return MimeTypeUtils.parseMimeType("image/webp");
    }
    return null;
  }

}
