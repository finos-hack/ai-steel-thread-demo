package org.finos.ai.demo.tokenomics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import org.finos.ai.demo.observability.Mi4ObservabilityProperties;

/** Builds Phase-3 optimization tips from ledger aggregates + the rate card. */
@Service
public class TokenomicsInsightService {

  private final TokenomicsProperties properties;
  private final TokenomicsLedger ledger;
  private final TokenomicsCostService costService;
  private final ObjectProvider<Mi4ObservabilityProperties> mi4Properties;
  private final ObjectProvider<TokenomicsService> tokenomicsService;

  public TokenomicsInsightService(
      TokenomicsProperties properties,
      TokenomicsLedger ledger,
      TokenomicsCostService costService,
      ObjectProvider<Mi4ObservabilityProperties> mi4Properties,
      ObjectProvider<TokenomicsService> tokenomicsService) {
    this.properties = properties;
    this.ledger = ledger;
    this.costService = costService;
    this.mi4Properties = mi4Properties;
    this.tokenomicsService = tokenomicsService;
  }

  public TokenomicsInsightsResponse insights(String useCaseFilter) {
    String useCase = useCaseFilter == null || useCaseFilter.isBlank() ? "all" : useCaseFilter;
    List<TokenomicsLedger.Entry> entries = ledger.streamFiltered(useCase, null, null).toList();
    List<TokenomicsInsightTip> tips = new ArrayList<>();

    Set<String> providers = new LinkedHashSet<>();
    Set<String> models = new LinkedHashSet<>();
    double totalCost = 0;
    for (TokenomicsLedger.Entry entry : entries) {
      providers.add(entry.provider());
      models.add(entry.model());
      totalCost += entry.estimatedCostUsd();
    }

    if (entries.isEmpty()) {
      tips.add(
          new TokenomicsInsightTip(
              "empty-ledger",
              "info",
              "No spend yet",
              "Run a loan instance to record live LLM spend by provider and model "
                  + "(selected on the start form for the credit-risk agent)."));
    } else {
      addDominantTaskTip(tips, useCase, totalCost);
      addDominantModelTip(tips, useCase, totalCost);
      addProviderMixTip(tips, providers, totalCost);
      addCheaperAlternativeTip(tips, useCase, totalCost);
      addBudgetTips(tips, useCase);
    }

    return new TokenomicsInsightsResponse(
        properties.getCurrency(),
        useCase,
        List.copyOf(tips),
        List.copyOf(providers),
        List.copyOf(models),
        grafanaUrl());
  }

  private void addDominantTaskTip(
      List<TokenomicsInsightTip> tips, String useCase, double totalCost) {
    if (totalCost <= 0) {
      return;
    }
    List<TokenomicsLedger.BreakdownRow> byTask = ledger.breakdown(useCase, null, null, "task");
    if (byTask.isEmpty()) {
      return;
    }
    TokenomicsLedger.BreakdownRow top = byTask.getFirst();
    double share = top.estimatedCostUsd() / totalCost;
    if (share >= 0.4) {
      tips.add(
          new TokenomicsInsightTip(
              "dominant-task",
              share >= 0.7 ? "warn" : "suggest",
              String.format(Locale.ROOT, "%.0f%% of spend on task %s", share * 100, top.key()),
              "Concentrate optimization on this task (prompt size, caching, or a cheaper model) "
                  + "before tuning lower-cost steps."));
    }
  }

  private void addDominantModelTip(
      List<TokenomicsInsightTip> tips, String useCase, double totalCost) {
    if (totalCost <= 0) {
      return;
    }
    List<TokenomicsLedger.BreakdownRow> byModel = ledger.breakdown(useCase, null, null, "model");
    if (byModel.isEmpty()) {
      return;
    }
    TokenomicsLedger.BreakdownRow top = byModel.getFirst();
    double share = top.estimatedCostUsd() / totalCost;
    if (share >= 0.5) {
      tips.add(
          new TokenomicsInsightTip(
              "dominant-model",
              "suggest",
              String.format(Locale.ROOT, "%.0f%% of spend on model %s", share * 100, top.key()),
              "Pin a smaller model for high-volume paths if quality gates still pass (mi-5 SAT)."));
    }
  }

  private void addProviderMixTip(
      List<TokenomicsInsightTip> tips, Set<String> providers, double totalCost) {
    if (providers.size() >= 2) {
      tips.add(
          new TokenomicsInsightTip(
              "multi-provider-active",
              "info",
              "Multi-provider spend detected",
              "Break down by provider to allocate cost across " + String.join(", ", providers) + "."));
      return;
    }
    if (totalCost > 0 && providers.size() == 1) {
      tips.add(
          new TokenomicsInsightTip(
              "single-provider",
              "info",
              "All spend on " + providers.iterator().next(),
              "Break down by model to compare gpt-5.4 vs gpt-5.6-luna spend from instance "
                  + "start parameters."));
    }
  }

  private void addCheaperAlternativeTip(
      List<TokenomicsInsightTip> tips, String useCase, double totalCost) {
    if (totalCost <= 0) {
      return;
    }
    List<TokenomicsLedger.BreakdownRow> byModel = ledger.breakdown(useCase, null, null, "model");
    if (byModel.isEmpty()) {
      return;
    }
    TokenomicsLedger.BreakdownRow top = byModel.getFirst();
    // Find a cheaper rate on the card for comparison.
    TokenomicsProperties.Rate expensive = costService.resolveRate("openai", top.key());
    if (expensive == null) {
      return;
    }
    TokenomicsProperties.Rate cheap = null;
    for (TokenomicsProperties.Rate rate : properties.getRates()) {
      if (rate == null) {
        continue;
      }
      if (rate.getPromptPer1m() + rate.getCompletionPer1m()
          < (expensive == null
              ? Double.MAX_VALUE
              : expensive.getPromptPer1m() + expensive.getCompletionPer1m())) {
        if (cheap == null
            || rate.getPromptPer1m() + rate.getCompletionPer1m()
                < cheap.getPromptPer1m() + cheap.getCompletionPer1m()) {
          cheap = rate;
        }
      }
    }
    if (cheap != null
        && expensive != null
        && !cheap.getModel().equalsIgnoreCase(top.key())
        && (cheap.getPromptPer1m() + cheap.getCompletionPer1m())
            < 0.85 * (expensive.getPromptPer1m() + expensive.getCompletionPer1m())) {
      tips.add(
          new TokenomicsInsightTip(
              "cheaper-rate",
              "suggest",
              "Cheaper rate available: "
                  + cheap.getProvider()
                  + "/"
                  + cheap.getModel(),
              String.format(
                  Locale.ROOT,
                  "Top spend model %s is pricier on the card than %s/%s ($%.2f+$%.2f vs $%.2f+$%.2f "
                      + "per 1M). Evaluate a shadow route for non-critical volume.",
                  top.key(),
                  cheap.getProvider(),
                  cheap.getModel(),
                  expensive.getPromptPer1m(),
                  expensive.getCompletionPer1m(),
                  cheap.getPromptPer1m(),
                  cheap.getCompletionPer1m())));
    }
  }

  private void addBudgetTips(List<TokenomicsInsightTip> tips, String useCase) {
    TokenomicsService service = tokenomicsService.getIfAvailable();
    if (service == null) {
      return;
    }
    List<TokenomicsBudgetView> budgets = new ArrayList<>(service.budgets(useCase).budgets());
    budgets.sort(Comparator.comparingDouble(TokenomicsBudgetView::burnRatio).reversed());
    for (TokenomicsBudgetView budget : budgets) {
      if ("exceeded".equals(budget.status())) {
        tips.add(
            new TokenomicsInsightTip(
                "budget-exceeded-" + budget.useCase(),
                "warn",
                "Budget exceeded for " + budget.useCase(),
                budget.hardLimit()
                    ? "Hard limit is on — new LLM calls for this use case are blocked."
                    : "Enable hard limit to stop further LLM spend, or raise the monthly limit."));
      } else if (budget.forecastExceedsLimit()) {
        tips.add(
            new TokenomicsInsightTip(
                "budget-forecast-" + budget.useCase(),
                "suggest",
                "On track to exceed budget for " + budget.useCase(),
                String.format(
                    Locale.ROOT,
                    "Linear forecast ~$%.4f vs $%.4f limit with %.1f days left in the period.",
                    budget.forecastEndOfPeriodUsd(),
                    budget.limitUsd(),
                    budget.remainingDays())));
      }
    }
  }

  private String grafanaUrl() {
    Mi4ObservabilityProperties mi4 = mi4Properties.getIfAvailable();
    String base =
        mi4 == null || mi4.getGrafanaBaseUrl() == null || mi4.getGrafanaBaseUrl().isBlank()
            ? "http://localhost:3000"
            : mi4.getGrafanaBaseUrl().replaceAll("/$", "");
    String path = properties.getGrafanaDashboardPath();
    if (path == null || path.isBlank()) {
      path = "/d/fluxnova-ai-observability/fluxnova-ai-observability";
    }
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return base + path;
  }
}
