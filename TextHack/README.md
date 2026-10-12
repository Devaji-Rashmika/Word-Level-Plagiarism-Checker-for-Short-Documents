# TextHack — Advanced Algorithms Laboratory (vertical slice)

A working Java + HTML/JS app for running real algorithm queries over a small Indian-language sample corpus
(Hindi, Marathi, Tamil). **This is a partial build. See "Status" — nothing unfinished is presented as finished.**

## Status

| Module | Implemented and tested | Not implemented yet |
|---|---|---|
| 1 System | Query console, history, examples, REST API, sample corpus, status page | Algorithm catalog UI, progress panel, full API docs page, light/dark is basic |
| 2 Strings | Naive, KMP, Z, Rabin–Karp (double hash, collisions counted and verified), Aho–Corasick, suffix array O(n log² n), Kasai LCP, longest common substring, word-overlap document similarity (hand-built hash set) | SA-IS, suffix automaton/tree, step-by-step visualizer, comparison chart |
| 3 DP | Levenshtein (table + traceback), Damerau (OSA) | Weighted edit, Needleman–Wunsch, Smith–Waterman, interval/bitmask/tree/SOS DP |
| 4 Flow | Edmonds–Karp (augmentation trace), Dinic, min cut | Graph editor, matching/König, min-cost flow, reductions |
| 5 NP / Approximation | — | Everything (`SCHEDULE` returns a clear "not implemented" error) |
| 6 Randomized / Parallel | Miller–Rabin (BigInteger; deterministic below 3.3e24, random bases above) | Randomized quicksort, universal hashing, reservoir sampling, all parallel demos |

Also not done: request timeouts (only input-size limits), the optional Wikipedia import script, the full docs set.

## Data honesty
`corpus/sample/*.txt` is **hand-written sample text, not Wikipedia**, and has not been reviewed by native speakers.
`corpus/metadata/citations.tsv` is an **invented** citation graph. Both are labeled as SAMPLE in every response.

## Run on Windows (JDK 17+ and Maven installed)
```
mvn test
mvn compile exec:java -Dexec.mainClass=texthack.api.Main -Dexec.args="8080 frontend corpus"
```
Open http://127.0.0.1:8080/ . Without Maven (JDK only):
```
mkdir out
javac -encoding UTF-8 -d out src\main\java\texthack\*\*.java
java -cp out texthack.api.Main 8080 frontend corpus
```
Note: the `pom.xml` was written but **never executed** (no Maven in the build sandbox); the JDK-only route was what got compiled and tested.

## Frontend
`frontend/index.html` is the DocSim page (Home, How it works, count, upload, processing, results with pair cards and matrix),
unchanged in look and wording. The only difference: the comparison runs in the **Java engine** (`POST /api/similarity`,
hand-built hash set) instead of in the browser. `.txt` is read locally; `.docx` (mammoth.js), `.pdf` (pdf.js) and the web fonts
load from CDNs and need internet. Uploaded text goes only to your local server.
The earlier console commands (SEARCH, FUZZY, SIMILAR, FLOW, PRIME) still exist as `POST /api/query` but have no screen in this UI.

## API
`GET /api/health` · `GET /api/algorithms` · `POST /api/query` body `{"command":"..."}` · `POST /api/similarity` body `{"names":["a.txt","b.txt"],"texts":["...","..."]}` (2-10 docs, 1,000,000 chars each, 12 MB body)
Success: `{"ok":true,"type":...}` · Failure: HTTP 400 `{"ok":false,"error":"..."}` (413 for bodies over 8 KB).

| Command | Example | Notes |
|---|---|---|
| SEARCH | `SEARCH "भारत" ALGO kmp` | ALGO = naive, kmp, z, rk, ac; pattern 1–200 code points |
| FUZZY | `FUZZY "algorithm" "algoritm"` | strings ≤ 300 code points; DP table returned when both ≤ 24 |
| SIMILAR | `SIMILAR hi-001 hi-002` | total length ≤ 20,000 code points |
| FLOW | `FLOW citation-network` | sample graph, ≤ 50 vertices |
| PRIME | `PRIME 561 ROUNDS 20` | ≤ 300 digits, ROUNDS 1–64 |

## Unicode behavior
Text is NFC-normalized and lower-cased per code point (so offsets match display text). Matching and positions are in
**code points**, not grapheme clusters or words: searching `भारत` also matches inside `भारतीय`. Indian-script
word segmentation, stemming and canonical-equivalence beyond NFC are not handled.

## Engine restriction
Packages `structures, strings, dynamicprogramming, flow, randomized` contain no `java.util` imports (checked by grep).
`java.math.BigInteger` is used for big-number storage and `multiply`/`mod`; the exponentiation and Miller–Rabin logic are hand-written.
The corpus and API layers use `java.util`, `java.nio` and `com.sun.net.httpserver`, kept outside the engine.

## Tests
`src/test/java/texthack/SelfTest.java` (295 checks; brute-force references are test-only). `EngineTest` wraps it for JUnit 5 (not executed here).
Covers empty/long patterns, repeated chars, Devanagari/Tamil text, forced hash collisions, suffix array vs brute force,
CLRS max-flow example, 200 random graphs (EK = Dinic), disconnected/zero-capacity graphs, primes, Carmichael numbers, a 127-bit Mersenne prime.
