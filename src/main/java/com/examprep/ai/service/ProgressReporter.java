package com.examprep.ai.service;

/** Reports human-readable generation progress to the persisted study package status. */
@FunctionalInterface
public interface ProgressReporter {

    void report(String message, int percent);
}
