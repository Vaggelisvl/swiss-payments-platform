package dev.vrailas.payments.assistant.domain;

/** Port to a language model. Receives only redacted input and a catalogue hint to stay grounded. */
public interface ExplanationModel {

    Explanation explain(ExceptionCase redacted, Explanation catalogueHint) throws ExplanationUnavailableException;
}
