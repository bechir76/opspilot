# OpsPilot

An AI agent that diagnoses Kubernetes incidents. It reads metrics, logs, events and recent Git changes, explains the root cause and proposes a fix that a human approves.

> **Status:** Phase 1 (demo workloads) is in progress. A full README will follow.

## Build

Requires JDK 21+ and Maven 3.9+.

```sh
mvn -B verify
```
