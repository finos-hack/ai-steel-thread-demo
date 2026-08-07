package org.finos.ai.demo.tokenomics;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.finos.ai.demo.observability.ProcessScopedLlmTokenUsageWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Records estimated LLM spend into Micrometer + an in-memory ledger after each {@code recordCall}.
 * Budgets, forecasting, and optional hard gate.
 */
@Service
public class TokenomicsService {

  private static final String COST_METRIC = "fluxnova.llm.cost";
  private static final double MS_PER_DAY = 86_400_000.0;
  /** Floor elapsed time so early-period burn does not explode the forecast. */
  private static final double MIN_ELAPSED_DAYS = 1.0 / 24.0;

  private final TokenomicsProperties properties;
  private final TokenomicsCostService costService;
  private final TokenomicsLedger ledger;
  private final MeterRegistry meterRegistry;
  private final ObjectProvider<ProcessScopedLlmTokenUsageWriter> processScopedWriter;
  private final Clock clock;

  public TokenomicsService(
      TokenomicsProperties properties,
      TokenomicsCostService costService,
      TokenomicsLedger ledger,
      MeterRegistry meterRegistry,
      ObjectProvider<ProcessScopedLlmTokenUsageWriter> processScopedWriter,
      ObjectProvider<Clock> clock) {
    this.properties = properties;
    this.costService = costService;
    this.ledger = ledger;
    this.meterRegistry = meterRegistry;
    this.processScopedWriter = processScopedWriter;
    Clock resolved = clock == null ? null : clock.getIfAvailable();
    this.clock = resolved == null ? Clock.systemUTC() : resolved;
  }

  /** Called from {@code LlmTokenUsageMetrics.recordCall}. Returns estimated USD or null. */
  public Double onLlmCall(
      String workflow,
      String task,
      String model,
      String provider,
      TokenUsage usage) {
    if (!properties.isEnabled() || usage == null) {
      return null;
    }
    Double cost = costService.estimateUsd(provider, model, usage);
    if (cost == null) {
      return null;
    }
    String useCase = properties.useCaseForWorkflow(workflow);
    String resolvedWorkflow = blankToUnknown(workflow);
    String resolvedTask = blankToUnknown(task);
    String resolvedModel = blankToUnknown(model);
    String resolvedProvider = blankToUnknown(provider);

    ledger.add(
        new TokenomicsLedger.Entry(
            Instant.now(clock),
            useCase,
            resolvedWorkflow,
            resolvedTask,
            resolvedProvider,
            resolvedModel,
            usage.promptTokens(),
            usage.completionTokens(),
            usage.totalTokens(),
            cost));

    meterRegistry
        .counter(
            COST_METRIC,
            "use_case",
            useCase,
            "workflow",
            resolvedWorkflow,
            "task",
            resolvedTask,
            "model",
            resolvedModel,
            "provider",
            resolvedProvider)
        .increment(cost);

    ProcessScopedLlmTokenUsageWriter writer = processScopedWriter.getIfAvailable();
    if (writer != null) {
      writer.accumulateEstimatedCostOnCurrentExecution(cost);
    }
    return cost;
  }

  /**
   * Pre-call hard budget check. Soft budgets only surface in the UI; hard budgets fail fast.
   */
  public void enforceHardBudget(String workflow) {
    if (!properties.isEnabled()) {
      return;
    }
    String useCase = properties.useCaseForWorkflow(workflow);
    TokenomicsProperties.Budget policy = findBudget(useCase);
    if (policy == null || !policy.isHardLimit()) {
      return;
    }
    TokenomicsBudgetView status = budgetStatus(policy);
    if (status.spentUsd() >= status.limitUsd()) {
      throw new TokenomicsBudgetExceededException(
          status.useCase(), status.spentUsd(), status.limitUsd());
    }
  }

  public TokenomicsSummaryResponse summary(String useCase, Instant from, Instant to) {
    var filtered = ledger.streamFiltered(useCase, from, to).toList();
    long calls = filtered.size();
    long prompt = 0;
    long completion = 0;
    long total = 0;
    double cost = 0;
    for (TokenomicsLedger.Entry entry : filtered) {
      prompt += entry.promptTokens();
      completion += entry.completionTokens();
      total += entry.totalTokens();
      cost += entry.estimatedCostUsd();
    }
    double avgCostPerCall = calls == 0 ? 0 : cost / calls;
    double costPer1kTokens = total == 0 ? 0 : (cost / total) * 1000.0;
    return new TokenomicsSummaryResponse(
        properties.isEnabled(),
        properties.getCurrency(),
        useCase == null || useCase.isBlank() ? "all" : useCase,
        calls,
        prompt,
        completion,
        total,
        TokenomicsCostService.roundUsd(cost),
        TokenomicsCostService.roundUsd(avgCostPerCall),
        TokenomicsCostService.roundUsd(costPer1kTokens),
        availableUseCases());
  }

  public TokenomicsBreakdownResponse breakdown(
      String useCase, Instant from, Instant to, String groupBy) {
    return new TokenomicsBreakdownResponse(
        properties.getCurrency(),
        useCase == null || useCase.isBlank() ? "all" : useCase,
        groupBy == null || groupBy.isBlank() ? "providerModel" : groupBy,
        ledger.breakdown(useCase, from, to, groupBy));
  }

  public TokenomicsRatesResponse rates() {
    List<TokenomicsRateView> views = new ArrayList<>();
    for (TokenomicsProperties.Rate rate : properties.getRates()) {
      views.add(
          new TokenomicsRateView(
              rate.getProvider(),
              rate.getModel(),
              rate.getPromptPer1m(),
              rate.getCompletionPer1m(),
              rate.getCacheInputPer1m(),
              rate.getCacheWritePer1m()));
    }
    return new TokenomicsRatesResponse(properties.getCurrency(), List.copyOf(views));
  }

  public TokenomicsBudgetsResponse budgets(String useCaseFilter) {
    List<TokenomicsBudgetView> views = new ArrayList<>();
    for (TokenomicsProperties.Budget budget : properties.getBudgets()) {
      if (budget == null || budget.getUseCase() == null || budget.getUseCase().isBlank()) {
        continue;
      }
      if (useCaseFilter != null
          && !useCaseFilter.isBlank()
          && !"all".equalsIgnoreCase(useCaseFilter)
          && !useCaseFilter.equals(budget.getUseCase())) {
        continue;
      }
      views.add(budgetStatus(budget));
    }
    return new TokenomicsBudgetsResponse(properties.getCurrency(), List.copyOf(views));
  }

  public TokenomicsBudgetsResponse updateBudget(TokenomicsBudgetUpdate update) {
    if (update == null || update.useCase() == null || update.useCase().isBlank()) {
      throw new IllegalArgumentException("useCase is required");
    }
    TokenomicsProperties.Budget budget = findBudget(update.useCase());
    if (budget == null) {
      budget = new TokenomicsProperties.Budget();
      budget.setUseCase(update.useCase().trim());
      properties.getBudgets().add(budget);
    }
    if (update.limitUsd() != null) {
      if (update.limitUsd() < 0) {
        throw new IllegalArgumentException("limitUsd must be >= 0");
      }
      budget.setLimitUsd(update.limitUsd());
    }
    if (update.warnAt() != null) {
      if (update.warnAt() < 0 || update.warnAt() > 1) {
        throw new IllegalArgumentException("warnAt must be between 0 and 1");
      }
      budget.setWarnAt(update.warnAt());
    }
    if (update.hardLimit() != null) {
      budget.setHardLimit(update.hardLimit());
    }
    if (update.period() != null && !update.period().isBlank()) {
      budget.setPeriod(update.period().trim().toLowerCase(Locale.ROOT));
    }
    return budgets(null);
  }

  TokenomicsBudgetView budgetStatus(TokenomicsProperties.Budget policy) {
    PeriodWindow window = periodWindow(policy.getPeriod());
    double spent =
        ledger
            .streamFiltered(policy.getUseCase(), window.start(), window.endExclusive())
            .mapToDouble(TokenomicsLedger.Entry::estimatedCostUsd)
            .sum();
    spent = TokenomicsCostService.roundUsd(spent);
    double limit = Math.max(policy.getLimitUsd(), 0);
    double remaining = TokenomicsCostService.roundUsd(Math.max(limit - spent, 0));
    double burnRatio = limit <= 0 ? (spent > 0 ? 1.0 : 0.0) : spent / limit;
    String status;
    if (spent >= limit && limit > 0) {
      status = "exceeded";
    } else if (burnRatio >= policy.getWarnAt()) {
      status = "warn";
    } else {
      status = "ok";
    }

    Instant now = Instant.now(clock);
    double elapsedDays =
        Math.max((now.toEpochMilli() - window.start().toEpochMilli()) / MS_PER_DAY, MIN_ELAPSED_DAYS);
    double remainingDays =
        Math.max((window.endExclusive().toEpochMilli() - now.toEpochMilli()) / MS_PER_DAY, 0);
    double forecast =
        spent <= 0
            ? 0
            : TokenomicsCostService.roundUsd(spent + (spent / elapsedDays) * remainingDays);
    boolean forecastExceeds = limit > 0 && forecast > limit;

    return new TokenomicsBudgetView(
        policy.getUseCase(),
        policy.getPeriod() == null ? "monthly" : policy.getPeriod(),
        limit,
        policy.getWarnAt(),
        policy.isHardLimit(),
        spent,
        remaining,
        TokenomicsCostService.roundUsd(burnRatio),
        status,
        forecast,
        forecastExceeds,
        window.start(),
        window.endExclusive().minusMillis(1),
        TokenomicsCostService.roundUsd(elapsedDays),
        TokenomicsCostService.roundUsd(remainingDays));
  }

  private TokenomicsProperties.Budget findBudget(String useCase) {
    if (useCase == null || useCase.isBlank()) {
      return null;
    }
    for (TokenomicsProperties.Budget budget : properties.getBudgets()) {
      if (budget != null && useCase.equals(budget.getUseCase())) {
        return budget;
      }
    }
    return null;
  }

  private PeriodWindow periodWindow(String period) {
    String normalized = period == null ? "monthly" : period.trim().toLowerCase(Locale.ROOT);
    Instant now = Instant.now(clock);
    if (!"monthly".equals(normalized)) {
      // Demo default: treat unknown periods as the current UTC month.
      normalized = "monthly";
    }
    YearMonth month = YearMonth.from(now.atZone(ZoneOffset.UTC));
    Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant endExclusive = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return new PeriodWindow(start, endExclusive);
  }

  private List<String> availableUseCases() {
    Map<String, String> distinct = new LinkedHashMap<>();
    distinct.put("all", "all");
    properties.getUseCaseMap().values().forEach(id -> distinct.put(id, id));
    ledger.snapshot().forEach(entry -> distinct.put(entry.useCaseId(), entry.useCaseId()));
    return List.copyOf(distinct.keySet());
  }

  private static String blankToUnknown(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }

  private record PeriodWindow(Instant start, Instant endExclusive) {}
}
