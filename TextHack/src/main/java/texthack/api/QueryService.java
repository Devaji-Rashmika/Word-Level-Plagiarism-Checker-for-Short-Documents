package texthack.api;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import texthack.corpus.Corpus;
import texthack.dynamicprogramming.EditDistance;
import texthack.flow.FlowNetwork;
import texthack.randomized.MillerRabin;
import texthack.strings.AhoCorasick;
import texthack.strings.StringAlgos;
import texthack.strings.WordSimilarity;
import texthack.structures.StringHashSet;
import texthack.structures.IntList;

/** Query dispatcher: parses commands, validates input, routes to the engine, and builds JSON. */
public final class QueryService {
    static final int MAX_PATTERN = 200, MAX_FUZZY = 300, MAX_SIMILAR_TOTAL = 20000, MAX_DIGITS = 300;
    private final Corpus corpus;

    public QueryService(Corpus c) { this.corpus = c; }

    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch)); else b.append(ch);
            }
        }
        return b.append('"').toString();
    }
    static String err(String msg) { return "{\"ok\":false,\"error\":" + q(msg) + "}"; }
    static String cps(int[] a, int off, int len) { return new String(a, off, len); }

    public String execute(String command) {
        try {
            if (command == null || command.isBlank()) return err("Empty command. Type HELP.");
            if (command.length() > 1000) return err("Command too long (max 1000 characters).");
            List<String> a = tokenize(command.trim());
            String verb = a.get(0).toUpperCase();
            switch (verb) {
                case "HELP": return help();
                case "SEARCH": return search(a);
                case "FUZZY": return fuzzy(a);
                case "SIMILAR": return similar(a);
                case "FLOW": return flow(a);
                case "PRIME": return prime(a);
                case "SCHEDULE":
                    return err("SCHEDULE is not implemented yet (NP-hardness/approximation module is planned, not built).");
                default: return err("Unknown command '" + a.get(0) + "'. Type HELP.");
            }
        } catch (IllegalArgumentException e) {
            return err(e.getMessage());
        } catch (RuntimeException e) {
            return err("Internal error: " + e.getClass().getSimpleName());
        }
    }

    static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '"') {
                int j = s.indexOf('"', i + 1);
                if (j < 0) throw new IllegalArgumentException("Unterminated quote.");
                out.add(s.substring(i + 1, j));
                i = j + 1;
            } else {
                int j = i;
                while (j < n && !Character.isWhitespace(s.charAt(j))) j++;
                out.add(s.substring(i, j));
                i = j;
            }
        }
        if (out.isEmpty()) throw new IllegalArgumentException("Empty command.");
        return out;
    }

    private String help() {
        return "{\"ok\":true,\"type\":\"help\",\"commands\":["
            + q("SEARCH \"pattern\" [ALGO naive|kmp|z|rk|ac]") + ","
            + q("FUZZY \"string1\" \"string2\"") + ","
            + q("SIMILAR doc1 doc2") + ","
            + q("FLOW citation-network") + ","
            + q("PRIME <integer> [ROUNDS 1-64]") + ","
            + q("SCHEDULE project-demo   (not implemented yet)") + "]}";
    }

    private String search(List<String> a) {
        if (a.size() != 2 && a.size() != 4) throw new IllegalArgumentException("Usage: SEARCH \"pattern\" [ALGO naive|kmp|z|rk|ac]");
        String algo = "kmp";
        if (a.size() == 4) {
            if (!a.get(2).equalsIgnoreCase("ALGO")) throw new IllegalArgumentException("Expected ALGO, got " + a.get(2));
            algo = a.get(3).toLowerCase();
        }
        int[] p = Corpus.normalizeForSearch(a.get(1));
        if (p.length == 0 || p.length > MAX_PATTERN) throw new IllegalArgumentException("Pattern must be 1-" + MAX_PATTERN + " code points.");
        String complexity;
        switch (algo) {
            case "naive": complexity = "O(nm) worst case, O(n) typical"; break;
            case "kmp": complexity = "O(n + m) worst case"; break;
            case "z": complexity = "O(n + m) worst case"; break;
            case "rk": complexity = "O(n + m) expected, O(nm) worst case (double hash, hits verified)"; break;
            case "ac": complexity = "O(n + m + occurrences)"; break;
            default: throw new IllegalArgumentException("Unknown algorithm '" + algo + "'. Use naive, kmp, z, rk, ac.");
        }
        long nanos = 0;
        int total = 0, spurious = 0;
        StringBuilder docsJson = new StringBuilder();
        for (Corpus.Doc d : corpus.docs) {
            long t0 = System.nanoTime();
            IntList r;
            switch (algo) {
                case "naive": r = StringAlgos.naive(d.search, p); break;
                case "z": r = StringAlgos.zSearch(d.search, p); break;
                case "rk": {
                    StringAlgos.RKResult rk = StringAlgos.rabinKarp(d.search, p, 1_000_000_007L, 998_244_353L);
                    r = rk.matches; spurious += rk.spuriousHits; break;
                }
                case "ac": {
                    IntList pairs = new AhoCorasick(new int[][]{p}).search(d.search);
                    r = new IntList();
                    for (int i = 0; i < pairs.size(); i += 2) r.add(pairs.get(i));
                    break;
                }
                default: r = StringAlgos.kmp(d.search, p);
            }
            nanos += System.nanoTime() - t0;
            total += r.size();
            if (docsJson.length() > 0) docsJson.append(',');
            docsJson.append("{\"id\":").append(q(d.id)).append(",\"title\":").append(q(d.title))
                .append(",\"lang\":").append(q(d.lang)).append(",\"source\":").append(q(d.source))
                .append(",\"matches\":").append(r.size()).append(",\"snippets\":[");
            for (int i = 0; i < r.size() && i < 5; i++) {
                int pos = r.get(i), b0 = Math.max(0, pos - 20), e1 = Math.min(d.display.length, pos + p.length + 20);
                if (i > 0) docsJson.append(',');
                docsJson.append("{\"position\":").append(pos)
                    .append(",\"before\":").append(q(cps(d.display, b0, pos - b0)))
                    .append(",\"match\":").append(q(cps(d.display, pos, p.length)))
                    .append(",\"after\":").append(q(cps(d.display, pos + p.length, e1 - pos - p.length))).append('}');
            }
            docsJson.append("]}");
        }
        return "{\"ok\":true,\"type\":\"search\",\"algorithm\":" + q(algo) + ",\"complexity\":" + q(complexity)
            + ",\"pattern\":" + q(a.get(1)) + ",\"timeMicros\":" + nanos / 1000 + ",\"totalMatches\":" + total
            + (algo.equals("rk") ? ",\"hashCollisions\":" + spurious : "")
            + ",\"note\":\"Positions are code-point offsets in NFC-normalized text; matching is case-folded per code point and is not grapheme- or word-aware.\""
            + ",\"documents\":[" + docsJson + "]}";
    }

    private String fuzzy(List<String> a) {
        if (a.size() != 3) throw new IllegalArgumentException("Usage: FUZZY \"string1\" \"string2\"");
        int[] x = Corpus.normalizeForSearch(a.get(1)), y = Corpus.normalizeForSearch(a.get(2));
        if (x.length > MAX_FUZZY || y.length > MAX_FUZZY) throw new IllegalArgumentException("Each string must be at most " + MAX_FUZZY + " code points.");
        long t0 = System.nanoTime();
        EditDistance.Result r = EditDistance.levenshtein(x, y);
        int dl = EditDistance.damerauOsa(x, y);
        long micros = (System.nanoTime() - t0) / 1000;
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"type\":\"fuzzy\",\"levenshtein\":" + r.distance
            + ",\"damerauOSA\":" + dl + ",\"ops\":" + q(r.ops)
            + ",\"opsLegend\":\"M=match S=substitute D=delete from first I=insert from second\""
            + ",\"complexity\":\"O(nm) time, O(nm) space (table kept for traceback)\",\"timeMicros\":" + micros);
        if (x.length <= 24 && y.length <= 24) {
            sb.append(",\"rows\":[");
            for (int i = 0; i < x.length; i++) sb.append(i > 0 ? "," : "").append(q(cps(x, i, 1)));
            sb.append("],\"cols\":[");
            for (int j = 0; j < y.length; j++) sb.append(j > 0 ? "," : "").append(q(cps(y, j, 1)));
            sb.append("],\"table\":[");
            for (int i = 0; i <= x.length; i++) {
                sb.append(i > 0 ? ",[" : "[");
                for (int j = 0; j <= y.length; j++) sb.append(j > 0 ? "," : "").append(r.table[i][j]);
                sb.append(']');
            }
            sb.append(']');
        }
        return sb.append('}').toString();
    }

    private String similar(List<String> a) {
        if (a.size() != 3) throw new IllegalArgumentException("Usage: SIMILAR doc1 doc2   (e.g. SIMILAR hi-001 hi-002)");
        Corpus.Doc d1 = corpus.find(a.get(1)), d2 = corpus.find(a.get(2));
        if (d1 == null) throw new IllegalArgumentException("Unknown document id '" + a.get(1) + "'. Known: " + ids());
        if (d2 == null) throw new IllegalArgumentException("Unknown document id '" + a.get(2) + "'. Known: " + ids());
        if (d1.search.length + d2.search.length > MAX_SIMILAR_TOTAL) throw new IllegalArgumentException("Documents too large.");
        long t0 = System.nanoTime();
        int[] r = StringAlgos.longestCommonSubstring(d1.search, d2.search);
        long micros = (System.nanoTime() - t0) / 1000;
        String shared = r[0] > 0 ? cps(d1.display, r[1], r[0]) : "";
        return "{\"ok\":true,\"type\":\"similar\",\"doc1\":" + q(d1.id) + ",\"doc2\":" + q(d2.id)
            + ",\"longestCommonSubstringLength\":" + r[0] + ",\"sharedText\":" + q(shared)
            + ",\"startInDoc1\":" + r[1] + ",\"startInDoc2\":" + r[2]
            + ",\"algorithm\":\"suffix array (prefix doubling) + Kasai LCP on doc1 + separator + doc2\""
            + ",\"complexity\":\"O((n+m) log^2 (n+m)) time\",\"timeMicros\":" + micros
            + ",\"note\":\"Length is in code points; the shared text may start or end mid-word.\"}";
    }

    private String ids() {
        StringBuilder b = new StringBuilder();
        for (Corpus.Doc d : corpus.docs) b.append(b.length() > 0 ? ", " : "").append(d.id);
        return b.toString();
    }

    private String flow(List<String> a) {
        if (a.size() != 2 || !a.get(1).equalsIgnoreCase("citation-network"))
            throw new IllegalArgumentException("Usage: FLOW citation-network");
        int n = corpus.docs.size();
        int s = -1, t = -1;
        for (int i = 0; i < n; i++) {
            if (corpus.docs.get(i).id.equals(corpus.citationSource)) s = i;
            if (corpus.docs.get(i).id.equals(corpus.citationSink)) t = i;
        }
        if (s < 0 || t < 0 || n > 50 || corpus.citations.size() > 500) throw new IllegalArgumentException("Citation metadata missing or too large.");
        FlowNetwork g = new FlowNetwork(n);
        for (Corpus.Cite c : corpus.citations) {
            int u = idx(c.from), v = idx(c.to);
            if (u < 0 || v < 0) throw new IllegalArgumentException("Citation refers to unknown document.");
            g.addEdge(u, v, c.cap);
        }
        IntList trace = new IntList();
        long t0 = System.nanoTime();
        long ek = g.edmondsKarp(s, t, trace);
        long tEk = (System.nanoTime() - t0) / 1000;
        boolean[] side = g.minCutSourceSide(s);
        StringBuilder aug = new StringBuilder(), cut = new StringBuilder(), src = new StringBuilder();
        int p = 0, k = 0;
        while (p < trace.size()) {
            int len = trace.get(p++);
            if (k++ > 0) aug.append(',');
            aug.append("{\"path\":[");
            for (int i = 0; i < len; i++) aug.append(i > 0 ? "," : "").append(q(corpus.docs.get(trace.get(p + i)).id));
            p += len;
            aug.append("],\"bottleneck\":").append(trace.get(p++)).append('}');
        }
        int cutCap = 0;
        for (int e = 0; e < g.edgeSlots(); e += 2) {
            if (side[g.from(e)] && !side[g.to(e)]) {
                if (cut.length() > 0) cut.append(',');
                cut.append("{\"from\":").append(q(corpus.docs.get(g.from(e)).id)).append(",\"to\":").append(q(corpus.docs.get(g.to(e)).id))
                   .append(",\"capacity\":").append(g.capacity(e)).append(",\"flow\":").append(g.flow(e)).append('}');
                cutCap += g.capacity(e);
            }
        }
        for (int i = 0; i < n; i++) if (side[i]) src.append(src.length() > 0 ? "," : "").append(q(corpus.docs.get(i).id));
        int[] ph = new int[1];
        t0 = System.nanoTime();
        long di = g.dinic(s, t, ph);
        long tDi = (System.nanoTime() - t0) / 1000;
        return "{\"ok\":true,\"type\":\"flow\",\"dataLabel\":" + q(corpus.citationLabel) + ",\"source\":" + q(corpus.citationSource)
            + ",\"sink\":" + q(corpus.citationSink) + ",\"maxFlowEdmondsKarp\":" + ek + ",\"maxFlowDinic\":" + di
            + ",\"agree\":" + (ek == di) + ",\"dinicPhases\":" + ph[0]
            + ",\"edmondsKarpMicros\":" + tEk + ",\"dinicMicros\":" + tDi
            + ",\"augmentations\":[" + aug + "],\"minCutSourceSide\":[" + src + "],\"minCutEdges\":[" + cut + "]"
            + ",\"minCutCapacity\":" + cutCap + ",\"complexity\":\"Edmonds-Karp O(VE^2); Dinic O(V^2 E) in general\""
            + ",\"note\":\"Edge capacity = citation weight in the sample metadata. Max-flow equals min-cut capacity (max-flow min-cut theorem).\"}";
    }
    private int idx(String id) {
        for (int i = 0; i < corpus.docs.size(); i++) if (corpus.docs.get(i).id.equals(id)) return i;
        return -1;
    }

    private String prime(List<String> a) {
        if (a.size() != 2 && a.size() != 4) throw new IllegalArgumentException("Usage: PRIME <integer> [ROUNDS 1-64]");
        String num = a.get(1);
        if (num.length() > MAX_DIGITS) throw new IllegalArgumentException("At most " + MAX_DIGITS + " digits.");
        for (int i = 0; i < num.length(); i++) if (num.charAt(i) < '0' || num.charAt(i) > '9') throw new IllegalArgumentException("Only decimal digits 0-9 are allowed.");
        int rounds = 20;
        if (a.size() == 4) {
            if (!a.get(2).equalsIgnoreCase("ROUNDS")) throw new IllegalArgumentException("Expected ROUNDS.");
            try { rounds = Integer.parseInt(a.get(3)); } catch (NumberFormatException e) { throw new IllegalArgumentException("ROUNDS must be an integer."); }
            if (rounds < 1 || rounds > 64) throw new IllegalArgumentException("ROUNDS must be 1-64.");
        }
        BigInteger n = new BigInteger(num);
        long t0 = System.nanoTime();
        MillerRabin.Result r = MillerRabin.test(n, rounds, 0x5DEECE66DL);
        long micros = (System.nanoTime() - t0) / 1000;
        return "{\"ok\":true,\"type\":\"prime\",\"n\":" + q(n.toString()) + ",\"bits\":" + n.bitLength()
            + ",\"verdict\":" + q(r.verdict) + ",\"deterministic\":" + r.deterministic + ",\"roundsRun\":" + r.rounds
            + ",\"witness\":" + q(r.witness) + ",\"errorBound\":" + q(r.errorBound) + ",\"note\":" + q(r.note)
            + ",\"complexity\":\"O(k log^3 n) bit operations per k rounds (schoolbook multiplication)\",\"timeMicros\":" + micros + "}";
    }

    static final int MAX_DOCS = 10, MAX_DOC_CHARS = 1_000_000, MAX_COMMON_RETURNED = 300;

    /** Word-level comparison of 2-10 uploaded documents (hash-set overlap divided by each document's unique-word count). */
    public static String similarity(String[] names, String[] texts) {
        try {
            if (names == null || texts == null || names.length != texts.length) throw new IllegalArgumentException("names and texts must be arrays of equal length.");
            int n = texts.length;
            if (n < 2 || n > MAX_DOCS) throw new IllegalArgumentException("Provide between 2 and " + MAX_DOCS + " documents.");
            long t0 = System.nanoTime();
            StringHashSet[] sets = new StringHashSet[n];
            for (int i = 0; i < n; i++) {
                if (texts[i].length() > MAX_DOC_CHARS) throw new IllegalArgumentException("Document " + (i + 1) + " is larger than " + MAX_DOC_CHARS + " characters.");
                if (names[i].length() > 200) throw new IllegalArgumentException("Document name too long.");
                sets[i] = WordSimilarity.uniqueWords(texts[i]);
                if (sets[i].size() == 0) throw new IllegalArgumentException("Document " + (i + 1) + " (" + names[i] + ") contains no countable words.");
            }
            StringBuilder docs = new StringBuilder(), pairs = new StringBuilder(), matrix = new StringBuilder("[");
            double[][] m = new double[n][n];
            for (int i = 0; i < n; i++) {
                docs.append(i > 0 ? "," : "").append("{\"name\":").append(q(names[i])).append(",\"uniqueWords\":").append(sets[i].size()).append('}');
            }
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) {
                    String[] common = WordSimilarity.common(sets[a], sets[b]);
                    double ab = 100.0 * common.length / sets[a].size(), ba = 100.0 * common.length / sets[b].size();
                    m[a][b] = ab; m[b][a] = ba;
                    int shown = Math.min(common.length, MAX_COMMON_RETURNED);
                    if (pairs.length() > 0) pairs.append(',');
                    pairs.append("{\"a\":").append(a).append(",\"b\":").append(b).append(",\"commonCount\":").append(common.length)
                        .append(",\"simAB\":").append(String.format("%.4f", ab)).append(",\"simBA\":").append(String.format("%.4f", ba))
                        .append(",\"commonTruncated\":").append(common.length > shown).append(",\"commonWords\":[");
                    for (int i = 0; i < shown; i++) pairs.append(i > 0 ? "," : "").append(q(common[i]));
                    pairs.append("]}");
                }
            }
            for (int a = 0; a < n; a++) {
                matrix.append(a > 0 ? ",[" : "[");
                for (int b = 0; b < n; b++) matrix.append(b > 0 ? "," : "").append(a == b ? "null" : String.format("%.4f", m[a][b]));
                matrix.append(']');
            }
            matrix.append(']');
            long micros = (System.nanoTime() - t0) / 1000;
            return "{\"ok\":true,\"type\":\"similarity\",\"algorithm\":\"hash-set word overlap (hand-built StringHashSet)\""
                + ",\"complexity\":\"O(total words) expected\",\"timeMicros\":" + micros
                + ",\"docs\":[" + docs + "],\"pairs\":[" + pairs + "],\"matrix\":" + matrix + "}";
        } catch (IllegalArgumentException e) {
            return err(e.getMessage());
        }
    }

    public static String algorithms() {
        return "{\"ok\":true,\"algorithms\":["
            + "{\"name\":\"naive\",\"module\":\"strings\",\"status\":\"implemented\",\"time\":\"O(nm)\"},"
            + "{\"name\":\"kmp\",\"module\":\"strings\",\"status\":\"implemented\",\"time\":\"O(n+m)\"},"
            + "{\"name\":\"z\",\"module\":\"strings\",\"status\":\"implemented\",\"time\":\"O(n+m)\"},"
            + "{\"name\":\"rabin-karp (double hash)\",\"module\":\"strings\",\"status\":\"implemented\",\"time\":\"O(n+m) expected\"},"
            + "{\"name\":\"aho-corasick\",\"module\":\"strings\",\"status\":\"implemented\",\"time\":\"O(n+m+occ)\"},"
            + "{\"name\":\"suffix array + Kasai LCP\",\"module\":\"strings\",\"status\":\"implemented\",\"time\":\"O(n log^2 n) + O(n)\"},"
            + "{\"name\":\"levenshtein / damerau-OSA\",\"module\":\"dp\",\"status\":\"implemented\",\"time\":\"O(nm)\"},"
            + "{\"name\":\"word-overlap document similarity (hash set)\",\"module\":\"strings\",\"status\":\"implemented\",\"time\":\"O(total words)\"},"
            + "{\"name\":\"edmonds-karp\",\"module\":\"flow\",\"status\":\"implemented\",\"time\":\"O(VE^2)\"},"
            + "{\"name\":\"dinic\",\"module\":\"flow\",\"status\":\"implemented\",\"time\":\"O(V^2E)\"},"
            + "{\"name\":\"miller-rabin\",\"module\":\"randomized\",\"status\":\"implemented\",\"time\":\"O(k log^3 n)\"},"
            + "{\"name\":\"smith-waterman, needleman-wunsch, matrix-chain, TSP bitmask, SOS DP\",\"module\":\"dp\",\"status\":\"not implemented\"},"
            + "{\"name\":\"min-cost flow, bipartite matching, project selection\",\"module\":\"flow\",\"status\":\"not implemented\"},"
            + "{\"name\":\"reductions, vertex-cover approximation, set cover, scheduling\",\"module\":\"np\",\"status\":\"not implemented\"},"
            + "{\"name\":\"randomized quicksort, reservoir sampling, parallel scan\",\"module\":\"randomized/parallel\",\"status\":\"not implemented\"}]}";
    }
}
