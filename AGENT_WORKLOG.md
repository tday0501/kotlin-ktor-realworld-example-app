# Agent work log

Short record of how agents were used on this exercise — not a transcript. The useful part is the gap between asking an agent to do something and having a workflow that can trust the result.

## Walkthrough recording

https://www.tella.tv/video/coding-exercise-walkthrough-ai-agent-workflow-2u7s

## Harnesses and models

Cursor Agent (Grok 4.6) in the IDE, after a Plan-mode pass. Used to explore the repo, implement the Popular Articles slice, write tests, and iterate on toolchain failures.

## Representative instructions

- “Read the FDE exercise PDF and plan a solution that fits the existing app.”
- “Clone my fork and run `./gradlew test` / `./gradlew run` before implementing.”
- “Implement Popular Articles: `/api` prefix, create + favorite + popular list, tests, CI.”

## Where agents changed the approach

The exercise and README describe a working RealWorld API. A real compile and test run showed the opposite: articles/comments/profiles are stubs, most tests are `@Ignore`, routes had no `/api` prefix, and `./gradlew test` did not compile.

That moved the work from “add one query on a working stack” to a thin vertical slice: persist articles and favorites, wire the writes needed to prove the new read, leave the rest stubbed.

## How I verified

Generated code was not treated as correct until `./gradlew test` passed. Five new tests in `ArticlePopularControllerTest` cover empty, unauthenticated, order, pagination, and ties. The same suite ran on Homebrew JDK 17 and JDK 21 after the Gradle 8.7 / Kotlin 1.9.24 bump. Legacy controller tests stay `@Ignore`. Early familiarization used curls against `./gradlew run` (`GET /tags` 200, `GET /api/tags` 404 before the prefix change).

## What they got wrong

Asking “plan a solution that fits the existing app” produced a plan against a README that was not true. The first compile failed because `exposed:0.14.1` is gone from Maven Central and JCenter. A one-line Exposed bump then cascaded: 0.17.14 needs Kotlin 1.5+, which needs Gradle ≥ 6.1.1, and H2 2.2 dropped `JdbcConnection.getSession()`.

Two bugs I would not have found by reading the generated controllers: `UserDTO.validRegister()` / `validLogin()` required password and username to be **blank** (register returned 500 until inverted), and the first popular tests leaked across methods because H2 used a shared named in-memory database.

## What I would do differently next time

Trust the result only after a gate, not after a plausible plan. Next time I would run `./gradlew test` and one live request before any feature plan, pin Kotlin/Gradle/H2 together as soon as the first dependency 404s, and give the agent a hard “do not restore full CRUD” constraint so stubbed controllers do not turn into an unsolicited rewrite.
