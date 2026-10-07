package com.campusguard.translation;

import java.util.Map;

/**
 * Turns user-written text into another language.
 *
 * <p>Absent when no model is configured, the same way the model engine and the
 * investigator are: the forum then shows every post as written.
 */
public interface Translator {

    /** Recorded against each stored translation, so a model change stays visible. */
    String modelName();

    /**
     * @param texts keyed by an opaque id the answer must repeat
     * @param language a BCP 47 tag the service accepts
     * @return a translation for every key that came back usable; missing keys
     *     are retried on a later read rather than stored as failures
     */
    Map<String, String> translate(Map<String, String> texts, String language);
}
