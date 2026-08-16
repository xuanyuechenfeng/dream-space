package com.dreamspace.common;

/** A small framework-neutral probe shared by HTTP and non-HTTP service profiles. */
@FunctionalInterface
public interface ReadinessProbe {
    boolean ready();
}
