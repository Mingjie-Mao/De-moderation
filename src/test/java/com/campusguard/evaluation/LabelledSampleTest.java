package com.campusguard.evaluation;

import static com.campusguard.moderation.ModerationDecision.ALLOW;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The language tag, which the report's breakdown and the held-out set's balance
 * assertion both read.
 *
 * <p>Worth pinning because it is deliberately not a language detector, and the
 * cases where a crude rule gives the wrong answer are the ones somebody would
 * otherwise discover by disbelieving a report.
 */
class LabelledSampleTest {

    @Test
    void readsAnyHanCharacterAsChinese() {
        assertThat(sample("考试安排", "这门课的安排很奇怪").language()).isEqualTo("zh");
    }

    @Test
    void readsTextWithoutHanAsEnglish() {
        assertThat(sample("tutorial", "Selling a spare ticket, $65.").language()).isEqualTo("en");
    }

    /**
     * A Chinese title over an English quotation is still a post a Chinese-speaking
     * reviewer would be shown, and the check this replaced looked only at the body.
     */
    @Test
    void readsTheTitleAsWellAsTheBody() {
        assertThat(sample("代写论文", "Essay writing service, DM me").language()).isEqualTo("zh");
    }

    /**
     * Mixed scripts count as Chinese. Not an arbitrary tie-break: the question
     * this tag is asked is whether an engine handles the non-English half, and a
     * post carrying any Chinese belongs to that half.
     */
    @Test
    void countsMixedScriptAsChinese() {
        assertThat(sample(null, "Join our WeChat group 加微信 kkk999").language()).isEqualTo("zh");
    }

    @Test
    void treatsMissingTextAsEnglishRatherThanFailing() {
        assertThat(sample(null, null).language()).isEqualTo("en");
    }

    private LabelledSample sample(String title, String body) {
        return new LabelledSample("id", "NORMAL", ALLOW, title, body, SampleProvenance.AUTHORED, null, "note");
    }
}
