<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Proposal: expose raw used classes and candidate artifacts for duplicate-class cases (#280)

## Problem
When multiple resolved artifacts provide the same class, `maven-dependency-analyzer` maps each used class to a single artifact and reports analysis only in terms of that mapping:

- `DefaultProjectDependencyAnalyzer#buildClassToArtifactMap(...)` retains one provider per class (declared wins over undeclared, else first wins; if two declared artifacts contain the class only the first is credited).
- `ProjectDependencyAnalysis` carries `usedDeclared` / `usedUndeclared` / `unusedDeclared` keyed by `Artifact` (with usages), and discards all non-winning providers and any pre-mapping view.

Consumers (e.g. `maven-dependency-plugin:analyze*`) therefore suggest/attribute a single dependency even when the reference is ambiguous or when a shaded aggregate wins over the intended API jar. Users cannot see raw referenced types, alternative providers, or whether a suggestion is ambiguous. This is most visible with shaded/uber jars, overlapping APIs, and accidental duplicates.

**Related:** #313 / PR #315 (1.17.3) fixed the important subcase where a *declared* artifact should be credited over an undeclared duplicate, but the core information loss and ambiguity visibility remain (see #280 discussion).

## Goals
- Preserve current analysis semantics and public API behavior by default (backward compatible).
- Expose enough information for callers to: (1) inspect used/referenced classes before or alongside artifact attribution, (2) enumerate all candidate artifacts that provide a used class, (3) detect and report ambiguity (and optionally identify the load-order/selected winner).
- Enable richer `dependency:analyze` output (e.g. “class X found in A, B, C — selected A*”) without forcing a UX choice in the analyzer.

## Non-goals (initially)
- Changing default winner selection policy beyond what #315 already does (can be revisited separately).
- Solving duplicate-class detection/reporting in general (that remains `enforcer:banDuplicateClasses` territory); this proposal focuses on *used* references and their attribution.

## Proposed API changes

### `ProjectDependencyAnalysis` (additive)
Keep existing constructors/getters/behavior. Add optional data, defaulting to empty for existing construction paths:

1. **Raw used classes (pre-mapping / as observed)**
   - `Set<String> getUsedClassNames()` — all dependency class names referenced by analyzed bytecode (union of main/test usages considered by analysis), regardless of whether they mapped to an artifact/JDK exclusion.
   - Optionally `Set<String> getUnmappedUsedClassNames()` for referenced classes with no providing artifact in `artifactClassMap` (if any reach this layer).

2. **Candidates per class**
   - `Map<String, Set<Artifact>> getCandidateArtifactsByUsedClass()` (or `getProvidingArtifactsByUsedClass()`), containing for each used class the set of resolved artifacts that contain it (from `artifactClassMap`), ordered deterministically where order matters.
   - Convenience: `Set<String> getAmbiguousUsedClassNames()` — used classes with `candidates.size() > 1`.
   - Convenience: `Set<Artifact> getCandidateArtifacts(String className)`.

3. **Selected/winner (optional but useful)**
   - Expose the chosen artifact per class (current mapping result), e.g. `Map<String, Artifact> getSelectedArtifactByUsedClass()`, so callers can mark the winner among candidates.

**Construction/back-compat:** add a new constructor/builder or overloads accepting the additional structures; existing constructors delegate with empty maps/sets. `equals/hashCode/toString` should account for new fields only if included (consider compatibility) — prefer additive `toString` sections and document equality semantics.

### `DefaultProjectDependencyAnalyzer`
- Build and retain a **class -> all candidate artifacts** view from `artifactClassMap` (and declared set if needed for deterministic ordering), not only the collapsed `class -> chosen artifact` map.
- Continue to produce the existing single-artifact `usedDeclared/usedUndeclared/unusedDeclared` results exactly as today (using the same winner policy) so behavior is unchanged.
- Pass raw used class names, candidate map, and selected map into the returned `ProjectDependencyAnalysis`.
- Keep `buildClassToArtifactMap` semantics intact for existing callers; add an internal/helper method (e.g. `buildClassToCandidateArtifactsMap`) for the full view.

### `DependencyUsage` (optional, lower priority)
If finer-grained reporting is desired, usages could carry ambiguity metadata (e.g. candidate count or ids). This is a larger API ripple; prefer analysis-level maps first.

## Consumer impact (`maven-dependency-plugin`)
Out of scope for this repo, but enabled by the above:
- `dependency:analyze` / `analyze-only` / `analyze-report` can list all candidate artifacts for an ambiguous used-undeclared class, mark the selected/winner, and/or warn on ambiguity.
- Configuration could gate verbose/ambiguous output to avoid noisy defaults.

## Example (illustrative)
Referenced class `jakarta.activation.DataHandler` found in `org.glassfish.metro:webservices-api` (shaded, undeclared, selected by current rules*) and `jakarta.activation:jakarta.activation-api` (declared/undeclared depending on POM). Report:
- used class: `jakarta.activation.DataHandler`
- candidates: `[jakarta.activation:jakarta.activation-api, org.glassfish.metro:webservices-api]`
- selected: `org.glassfish.metro:webservices-api` (*subject to winner policy)
- ambiguous: `true`

## Testing
- Unit tests: mapping produces correct candidate sets and preserves current winner for single- and multi-provider classes (declared-over-undeclared, first-wins fallback, two declared providers).
- IT: duplicate-class scenario (e.g. class present in a declared jar and an undeclared jar; and in two undeclared jars) asserts existing used/unused/undeclared results unchanged and new candidate/raw data present.
- Regression: all existing analyzer/IT expectations remain valid.

## Open questions
- Should candidate ordering reflect classpath/load order (deterministic) vs grouped (declared first) vs unspecified? (Must be documented; deterministic preferred.)
- Should scope influence “winner” selection or be reported separately? (#315 explicitly did not consider scope except declared-vs-undeclared.)
- `equals/hashCode` compatibility for `ProjectDependencyAnalysis` when new fields are added.
- Naming: `candidate` vs `providing` artifacts; “raw used classes” vs “referenced classes”.
- Whether to surface ambiguity only where `selected` differs from what a human would expect, or for all multi-provider used classes.

## Implementation plan (incremental)
1. Add analysis-level data structures and accessors with defaults (no behavior change).
2. Compute and populate candidate/selected/raw-used maps in `DefaultProjectDependencyAnalyzer` alongside existing mapping.
3. Add unit/IT coverage for duplicate-class visibility while locking current outcomes.
4. Follow-up in `maven-dependency-plugin` to render ambiguity/candidates (separate change, version alignment).
