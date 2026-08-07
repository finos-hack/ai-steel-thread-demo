package org.finos.ai.demo.tokenomics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

/** In-memory ledger of estimated LLM spend for the demo control plane. */
@Component
public class TokenomicsLedger {

  private final ConcurrentLinkedQueue<Entry> entries = new ConcurrentLinkedQueue<>();

  public void add(Entry entry) {
    if (entry != null) {
      entries.add(entry);
    }
  }

  public List<Entry> snapshot() {
    return List.copyOf(entries);
  }

  public void clear() {
    entries.clear();
  }

  public Stream<Entry> streamFiltered(String useCase, Instant from, Instant to) {
    return entries.stream()
        .filter(
            entry -> {
              if (useCase != null && !useCase.isBlank() && !"all".equalsIgnoreCase(useCase)) {
                if (!useCase.equals(entry.useCaseId())) {
                  return false;
                }
              }
              if (from != null && entry.at().isBefore(from)) {
                return false;
              }
              if (to != null && entry.at().isAfter(to)) {
                return false;
              }
              return true;
            });
  }

  public List<BreakdownRow> breakdown(
      String useCase, Instant from, Instant to, String groupBy) {
    String dimension = groupBy == null || groupBy.isBlank() ? "providerModel" : groupBy;
    Map<String, MutableAgg> aggregates = new LinkedHashMap<>();
    streamFiltered(useCase, from, to)
        .forEach(
            entry -> {
              String key =
                  switch (dimension) {
                    case "useCase", "use-case" -> entry.useCaseId();
                    case "provider" -> entry.provider();
                    case "model" -> entry.model();
                    case "providerModel", "provider-model" ->
                        entry.provider() + "/" + entry.model();
                    case "workflow" -> entry.workflow();
                    default -> entry.task();
                  };
              MutableAgg agg = aggregates.computeIfAbsent(key, ignored -> new MutableAgg());
              agg.calls += 1;
              agg.promptTokens += entry.promptTokens();
              agg.completionTokens += entry.completionTokens();
              agg.totalTokens += entry.totalTokens();
              agg.estimatedCostUsd += entry.estimatedCostUsd();
            });
    List<BreakdownRow> rows = new ArrayList<>();
    aggregates.forEach(
        (key, agg) ->
            rows.add(
                new BreakdownRow(
                    key,
                    agg.calls,
                    agg.promptTokens,
                    agg.completionTokens,
                    agg.totalTokens,
                    TokenomicsCostService.roundUsd(agg.estimatedCostUsd))));
    rows.sort(Comparator.comparingDouble(BreakdownRow::estimatedCostUsd).reversed());
    return rows;
  }

  public record Entry(
      Instant at,
      String useCaseId,
      String workflow,
      String task,
      String provider,
      String model,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      double estimatedCostUsd) {}

  public record BreakdownRow(
      String key,
      long calls,
      long promptTokens,
      long completionTokens,
      long totalTokens,
      double estimatedCostUsd) {}

  private static final class MutableAgg {
    long calls;
    long promptTokens;
    long completionTokens;
    long totalTokens;
    double estimatedCostUsd;
  }
}
