package com.campusguard.evaluation;

import static com.campusguard.moderation.ModerationDecision.ALLOW;
import static com.campusguard.moderation.ModerationDecision.REMOVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.campusguard.moderation.ModerationDecision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The per-slice tables, and the two ways a slice's macro-F1 lies.
 *
 * <p>Both were found by reading a real report rather than by thinking about it,
 * which is why they are pinned here.
 */
class StratifiedReportTest {

    private final EvaluationReportWriter writer = new EvaluationReportWriter();

    /**
     * The case that prompted the check. A slice expecting one answer almost
     * everywhere gets a second class the moment a single sample is wrong, that
     * class scores zero, and the mean of the two halves the figure while accuracy
     * barely moves — 0.491 against 0.964 in the run this came from.
     */
    @Test
    void withholdsMacroF1FromASliceOneLabelDominates() {
        List<SampleOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            outcomes.add(outcome("ok" + i, "en", ALLOW, ALLOW));
        }
        outcomes.add(outcome("miss", "en", ALLOW, REMOVE));
        // A second language, only so the dimension has two values and the table
        // is printed at all. The en slice is what this asserts on.
        outcomes.add(outcome("zh1", "zh", ALLOW, ALLOW));
        outcomes.add(outcome("zh2", "zh", REMOVE, REMOVE));

        String report = render(outcomes);

        assertThat(report).contains("| en | 28 | 0.964 | — |");
        assertThat(report).contains("expected answer covers 80% of them");
    }

    /** A slice with a real spread of answers keeps its macro-F1, or the column is pointless. */
    @Test
    void keepsMacroF1WhereTheLabelsAreSpread() {
        List<SampleOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            outcomes.add(outcome("a" + i, "en", ALLOW, ALLOW));
        }
        for (int i = 0; i < 12; i++) {
            outcomes.add(outcome("r" + i, "en", REMOVE, REMOVE));
        }
        outcomes.add(outcome("zh1", "zh", ALLOW, ALLOW));

        assertThat(render(outcomes)).contains("| en | 24 | 1.000 | 1.000 |");
    }

    /** One value is the headline number with extra steps, so no table is printed. */
    @Test
    void printsNoTableForADimensionWithOneValue() {
        List<SampleOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            outcomes.add(outcome("a" + i, "en", i % 2 == 0 ? ALLOW : REMOVE, ALLOW));
        }

        assertThat(render(outcomes)).doesNotContain("by the language the sample is written in");
    }

    @Test
    void splitsALanguageDimensionThatHasTwoValues() {
        List<SampleOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            outcomes.add(outcome("e" + i, "en", i % 2 == 0 ? ALLOW : REMOVE, ALLOW));
            outcomes.add(outcome("z" + i, "zh", i % 2 == 0 ? ALLOW : REMOVE, ALLOW));
        }

        String report = render(outcomes);
        assertThat(report).contains("by the language the sample is written in");
        assertThat(report).contains("| en | 12 |").contains("| zh | 12 |");
    }

    private String render(List<SampleOutcome> outcomes) {
        ConfusionMatrix matrix = new ConfusionMatrix();
        outcomes.stream()
                .filter(outcome -> !outcome.failed())
                .forEach(outcome -> matrix.record(outcome.expected(), outcome.actual()));

        EvaluationResult result = new EvaluationResult(
                "engine/v1", "dataset", EngineRunStatus.OK, matrix, outcomes, null, null);

        return writer.render(new BenchmarkReport(
                Instant.parse("2026-09-30T00:00:00Z"), "dataset", outcomes.size(), false,
                List.of(result), List.of(), List.of(), List.of()));
    }

    private SampleOutcome outcome(
            String id, String language, ModerationDecision expected, ModerationDecision actual) {
        return new SampleOutcome(
                id, null, "NORMAL", language, SampleProvenance.AUTHORED, expected, actual, 0.9, List.of(),
                "because", "excerpt", 100, null, null, null);
    }
}
