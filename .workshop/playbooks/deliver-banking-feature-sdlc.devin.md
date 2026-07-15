# Playbook: Deliver a brownfield banking feature end-to-end (with the verification loop)

## Overview

Use this to deliver a **new feature into an existing banking microservice** the
whole way through the SDLC — requirements, technical design, implementation,
tests, and quality gates — landing as a review-ready PR. The point is not to
type code fast; it is to run the *full* lifecycle over an unfamiliar brownfield
codebase and **prove** the result against a programmatic verification loop before
anyone reads a line of the diff.

The one guiding principle: **the test suite is the source of truth.** A feature
is "done" when the service's test task is green with tests that assert the new
behavior — not when the code compiles or "looks reasonable."

## Required from user

- **The service and module** to work in (e.g. a Spring Boot service module that
  builds and tests independently).
- **The feature** stated as an outcome, with acceptance criteria (endpoint shape,
  filters, ordering, pagination, error cases).
- **The verification** — the module's test command and the existing test class to
  follow for style. If the module has no runnable tests, add the harness first;
  verification is the heart of this procedure.
- **The existing symbols to extend** — the controller, service, repository, and
  entity the feature touches, so the change follows established patterns.

## Procedure

1. **Orient before writing.** Read the existing controller, service, repository,
   and entity for the feature area, plus the persistence schema (migrations) and
   the existing test class. Use DeepWiki over the repo to map patterns quickly
   (coverage depends on repo structure). Do not invent new patterns where the
   repo already has one.
2. **Requirements (`SPEC.md`).** Write a short spec grounded in the code you just
   read: the user need, the endpoint contract, filters/ordering/pagination, and
   explicit acceptance criteria and edge cases (empty result, boundary values).
3. **Technical design (`DESIGN.md`).** Record the design decisions: schema change
   (with migration), DTO shape, repository query, service mapping/filtering, and
   the controller endpoint — each tied to an existing pattern in the repo.
4. **Implement against the patterns.** Add the migration, extend the entity, add
   the DTO, the repository query, the service method, and the endpoint — matching
   the surrounding style (annotations, naming, constructor injection, records for
   DTOs where the repo does).
5. **Write tests that assert the new behavior.** Follow the existing test class.
   Cover the happy path, the empty result, and every filter/ordering/boundary in
   the acceptance criteria. These tests are the gate, so make them assert the
   *contract*, not the implementation.
6. **Run the verification loop.** Run the module's test task. When it fails, read
   the failure, fix the code against the acceptance criteria (not the test), and
   re-run until green. Capture the caught divergence — it is the credibility
   beat.
7. **Summarize the lifecycle in the PR.** State what changed at each stage
   (spec → design → impl → tests), which acceptance criteria are met, and how the
   change was verified (the green test run).

## Specifications (postconditions)

- `SPEC.md` and `DESIGN.md` exist at the module root and reflect the shipped code.
- The feature is implemented across migration, entity, DTO, repository, service,
  and controller, each consistent with existing repo patterns.
- New tests assert the happy path, the empty result, and each
  filter/ordering/boundary criterion, and the module's test task is **green**.
- The PR description walks the lifecycle and names the verification that proves
  the result.
- The change is confined to the target module; unrelated files are untouched.

## Advice and pointers

- Lead with the tests. If a reviewer remembers one thing, it should be "the
  behavior is asserted and the suite is green."
- Read the schema before designing the query — ordering and filtering usually
  live in the repository layer, and boundary semantics (inclusive vs exclusive)
  are where real bugs hide.
- Keep the spec and design short and grounded in the actual symbols; they exist
  to make the change reviewable, not to pad the PR.
- No overstatement for probabilistic steps (DeepWiki, AI analysis) — say
  "typically" / "coverage depends on repo structure."

## Forbidden actions

- Do **not** call a feature done on a compile alone — the test task must be green
  with tests that assert the new behavior.
- Do **not** edit a test to make it pass; fix the code against the acceptance
  criteria, or flag the test as wrong.
- Do **not** invent new patterns where the repo already establishes one.
- Do **not** include customer-identifying content or identify the requester in
  the PR or commits.

## Worked example — a real divergence the verification loop caught

Delivering an "Account Statement & Transaction History" endpoint
(`GET /api/v1/account/{accountNumber}/transactions`) with date-range filtering,
the first implementation filtered the range with strictly exclusive bounds
(`timestamp.isAfter(from) && timestamp.isBefore(to)`). It compiled and the happy
path passed. The boundary test failed:

```
TransactionServiceTest > getTransactionHistory_includesBoundaryTransactions FAILED
  org.opentest4j.AssertionFailedError:
  Expected size: 3 but was: 1
  A transaction whose timestamp equals the range end was dropped.
```

The acceptance criteria said the range is **inclusive** on both ends. The fix was
to use inclusive comparison (Spring Data `Between` is inclusive, or `!isAfter` /
`!isBefore`), not to relax the test. Re-running the suite went green:

```
BUILD SUCCESSFUL
  TransactionServiceTest — all tests PASSED
```

A "compiles and looks reasonable" review would have shipped a statement that
silently omits the last transaction of a period — exactly the kind of defect a
banking reconciliation would surface in production. The boundary test caught it
before the PR was opened.
