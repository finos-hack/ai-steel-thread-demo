package org.finos.ai.demo.tokenomics;

import java.util.List;
import java.util.Locale;

import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.springframework.stereotype.Service;

/** Estimates USD cost from the configured rate card. */
@Service
public class TokenomicsCostService {

  private final TokenomicsProperties properties;

  public TokenomicsCostService(TokenomicsProperties properties) {
    this.properties = properties;
  }

  public Double estimateUsd(String provider, String model, TokenUsage usage) {
    if (!properties.isEnabled() || usage == null) {
      return null;
    }
    TokenomicsProperties.Rate rate = resolveRate(provider, model);
    if (rate == null) {
      return null;
    }

    int cacheRead = Math.max(0, usage.cacheReadInputTokens());
    int cacheWrite = Math.max(0, usage.cacheWriteInputTokens());
    int promptBillable = Math.max(0, usage.promptTokens());

    // OpenAI reports cached tokens inside prompt_tokens — avoid double-charging at the full
    // prompt rate. Anthropic reports cache buckets separately from input_tokens.
    if (isOpenAi(provider) && cacheRead > 0) {
      promptBillable = Math.max(0, usage.promptTokens() - cacheRead);
    }

    double prompt = promptBillable * rate.getPromptPer1m() / 1_000_000.0;
    double completion = usage.completionTokens() * rate.getCompletionPer1m() / 1_000_000.0;
    double cacheInput = cacheRead * rate.getCacheInputPer1m() / 1_000_000.0;
    double cacheWriteCost = cacheWrite * rate.getCacheWritePer1m() / 1_000_000.0;
    return roundUsd(prompt + completion + cacheInput + cacheWriteCost);
  }

  public TokenomicsProperties.Rate resolveRate(String provider, String model) {
    String p = normalize(provider);
    String m = normalize(model);
    List<TokenomicsProperties.Rate> rates = properties.getRates();
    TokenomicsProperties.Rate wildcard = null;
    for (TokenomicsProperties.Rate rate : rates) {
      if (rate == null) {
        continue;
      }
      if (!p.equals(normalize(rate.getProvider()))) {
        continue;
      }
      String rateModel = normalize(rate.getModel());
      if (m.equals(rateModel)) {
        return rate;
      }
      if ("*".equals(rateModel)) {
        wildcard = rate;
      }
    }
    return wildcard;
  }

  private static boolean isOpenAi(String provider) {
    return "openai".equals(normalize(provider));
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
  }

  static double roundUsd(double value) {
    return Math.round(value * 1_000_000.0) / 1_000_000.0;
  }
}
