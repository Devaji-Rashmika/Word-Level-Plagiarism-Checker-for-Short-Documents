package texthack;

import java.math.BigInteger;
import texthack.dynamicprogramming.EditDistance;
import texthack.flow.FlowNetwork;
import texthack.randomized.MillerRabin;
import texthack.strings.AhoCorasick;
import texthack.strings.StringAlgos;
import texthack.strings.WordSimilarity;
import texthack.structures.IntList;
import texthack.structures.StringHashSet;

/** Plain-Java test runner (also wrapped by JUnit in EngineTest). Test-only brute-force references live here. */
public final class SelfTest {
    static int checks, failures;

    static void check(boolean ok, String name) {
        checks++;
        if (!ok) { failures++; System.out.println("FAIL: " + name); }
    }
    static int[] cp(String s) { return s.codePoints().toArray(); }
    static boolean same(IntList a, IntList b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    static void strings() {
        String[][] cases = {
            {"", ""}, {"abc", ""}, {"", "a"}, {"ab", "abc"}, {"aaaaaa", "aa"}, {"abababab", "abab"},
            {"भारत भारतीय भारत", "भारत"}, {"இந்தியா இந்தியா", "இந்தியா"}, {"महाराष्ट्र", "ष्ट्र"}, {"mississippi", "issi"}
        };
        for (String[] c : cases) {
            int[] t = cp(c[0]), p = cp(c[1]);
            IntList ref = StringAlgos.naive(t, p);
            check(same(ref, StringAlgos.kmp(t, p)), "kmp " + c[0] + "|" + c[1]);
            check(same(ref, StringAlgos.zSearch(t, p)), "z " + c[0] + "|" + c[1]);
            check(same(ref, StringAlgos.rabinKarp(t, p, 1_000_000_007L, 998_244_353L).matches), "rk " + c[0]);
            IntList ac = new AhoCorasick(new int[][]{p}).search(t);
            IntList starts = new IntList();
            for (int i = 0; i < ac.size(); i += 2) starts.add(ac.get(i));
            check(same(ref, starts), "ac " + c[0] + "|" + c[1]);
        }
        // hash collisions: tiny moduli force spurious hits, but results stay exact
        StringAlgos.RKResult r = StringAlgos.rabinKarp(cp("abcdefghijabcdefghij"), cp("hij"), 2, 3);
        check(r.spuriousHits > 0, "collisions are forced and counted");
        check(same(StringAlgos.naive(cp("abcdefghijabcdefghij"), cp("hij")), r.matches), "rk exact despite collisions");
        // Aho-Corasick multi-pattern with overlaps: he, she, his, hers in ushers
        IntList ac = new AhoCorasick(new int[][]{cp("he"), cp("she"), cp("his"), cp("hers")}).search(cp("ushers"));
        check(ac.size() == 6, "aho-corasick finds she,he,hers (got " + ac.size() / 2 + ")");
        // suffix array vs brute force sort
        String[] words = {"banana", "mississippi", "aaaa", "a", "", "भारतभारत"};
        for (String w : words) {
            int[] s = cp(w), sa = StringAlgos.suffixArray(s), lcp = StringAlgos.kasaiLcp(s, sa);
            boolean ok = sa.length == s.length;
            for (int i = 1; i < sa.length && ok; i++) {
                int a = sa[i - 1], b = sa[i], k = 0;
                while (a + k < s.length && b + k < s.length && s[a + k] == s[b + k]) k++;
                boolean less = (a + k == s.length) || (b + k < s.length && s[a + k] < s[b + k]);
                ok = less && lcp[i] == k;
            }
            check(ok, "suffix array/LCP " + w);
        }
        int[] lcs = StringAlgos.longestCommonSubstring(cp("xabcdy"), cp("zzabcdw"));
        check(lcs[0] == 4 && lcs[1] == 1 && lcs[2] == 2, "longest common substring");
        check(StringAlgos.longestCommonSubstring(cp("abc"), cp("xyz"))[0] == 0, "no common substring");
    }

    static void dp() {
        check(EditDistance.levenshtein(cp("kitten"), cp("sitting")).distance == 3, "kitten/sitting");
        check(EditDistance.levenshtein(cp(""), cp("abc")).distance == 3, "empty/abc");
        check(EditDistance.levenshtein(cp(""), cp("")).distance == 0, "empty/empty");
        check(EditDistance.levenshtein(cp("algorithm"), cp("algoritm")).distance == 1, "algorithm/algoritm");
        check(EditDistance.levenshtein(cp("भारत"), cp("भारथ")).distance == 1, "devanagari substitution");
        EditDistance.Result r = EditDistance.levenshtein(cp("kitten"), cp("sitting"));
        int cost = 0;
        for (int i = 0; i < r.ops.length(); i++) if (r.ops.charAt(i) != 'M') cost++;
        check(cost == r.distance, "traceback cost equals distance");
        check(EditDistance.damerauOsa(cp("ab"), cp("ba")) == 1, "transposition costs 1");
        check(EditDistance.levenshtein(cp("ab"), cp("ba")).distance == 2, "levenshtein transposition costs 2");
        check(EditDistance.damerauOsa(cp("ca"), cp("abc")) == 3, "OSA ca/abc = 3");
    }

    static void flow() {
        FlowNetwork g = new FlowNetwork(6);   // CLRS example, max flow 23
        int[][] e = {{0,1,16},{0,2,13},{1,2,10},{2,1,4},{1,3,12},{3,2,9},{2,4,14},{4,3,7},{3,5,20},{4,5,4}};
        for (int[] x : e) g.addEdge(x[0], x[1], x[2]);
        IntList trace = new IntList();
        long ek = g.edmondsKarp(0, 5, trace);
        boolean[] side = g.minCutSourceSide(0);
        long cut = 0;
        for (int i = 0; i < g.edgeSlots(); i += 2) if (side[g.from(i)] && !side[g.to(i)]) cut += g.capacity(i);
        int[] ph = new int[1];
        long di = g.dinic(0, 5, ph);
        check(ek == 23 && di == 23, "CLRS max flow 23 (ek=" + ek + ", dinic=" + di + ")");
        check(cut == 23, "min cut equals max flow");
        check(ph[0] >= 1 && trace.size() > 0, "dinic phases and EK trace recorded");
        FlowNetwork d = new FlowNetwork(4);   // disconnected sink + zero capacity
        d.addEdge(0, 1, 5); d.addEdge(2, 3, 5);
        check(d.edmondsKarp(0, 3, null) == 0 && d.dinic(0, 3, null) == 0, "disconnected graph flow 0");
        FlowNetwork z = new FlowNetwork(2);
        z.addEdge(0, 1, 0);
        check(z.edmondsKarp(0, 1, null) == 0, "zero-capacity edge");
        boolean threw = false;
        try { z.dinic(0, 0, null); } catch (IllegalArgumentException ex) { threw = true; }
        check(threw, "source == sink rejected");
        // random graphs: EK == Dinic
        long seed = 12345;
        for (int round = 0; round < 200; round++) {
            int n = 6 + (int) ((seed >>> 3) % 5);
            FlowNetwork h = new FlowNetwork(n);
            for (int k = 0; k < 20; k++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int u = (int) ((seed >>> 33) % n), v = (int) ((seed >>> 40) % n), c = (int) ((seed >>> 48) % 10);
                if (u != v) h.addEdge(u, v, c);
            }
            check(h.edmondsKarp(0, n - 1, null) == h.dinic(0, n - 1, null), "random EK == Dinic round " + round);
        }
    }

    static void primes() {
        long[] primes = {2, 3, 5, 97, 1_000_000_007L, 998_244_353L};
        for (long p : primes) check("PRIME".equals(MillerRabin.test(BigInteger.valueOf(p), 20, 1).verdict), "prime " + p);
        long[] comps = {4, 9, 91, 561, 1105, 1729, 2465, 41041, 825265, 3215031751L, 1_000_000_007L * 3};
        for (long c : comps) check("COMPOSITE".equals(MillerRabin.test(BigInteger.valueOf(c), 20, 1).verdict), "composite/Carmichael " + c);
        check("NEITHER".equals(MillerRabin.test(BigInteger.ONE, 5, 1).verdict), "1 is neither");
        MillerRabin.Result sp = MillerRabin.test(BigInteger.valueOf(3215031751L), 20, 1);
        check("COMPOSITE".equals(sp.verdict) && sp.witness.startsWith("base"), "3215031751 (strong pseudoprime to 2,3,5,7) caught by a witness, got " + sp.witness);
        check("PRIME".equals(MillerRabin.test(new BigInteger("170141183460469231731687303715884105727"), 20, 1).verdict) ||
              "PROBABLE_PRIME".equals(MillerRabin.test(new BigInteger("170141183460469231731687303715884105727"), 20, 1).verdict), "M127 Mersenne prime");
        BigInteger big = new BigInteger("170141183460469231731687303715884105727");
        check("COMPOSITE".equals(MillerRabin.test(big.multiply(big), 20, 1).verdict), "M127^2 composite");
        BigInteger m = new BigInteger("1000000007");
        check(MillerRabin.modPow(BigInteger.TWO, new BigInteger("1000000006"), m).equals(BigInteger.ONE), "Fermat 2^(p-1) mod p == 1");
        // modPow vs BigInteger.modPow (test-only reference)
        BigInteger a = new BigInteger("123456789123456789"), e = new BigInteger("987654321987654321"), n = new BigInteger("1000000000000000003");
        check(MillerRabin.modPow(a, e, n).equals(a.modPow(e, n)), "modPow matches reference");
    }

    static void words() {
        StringHashSet h = new StringHashSet();
        for (int i = 0; i < 5000; i++) h.add("w" + i);
        for (int i = 0; i < 5000; i++) h.add("w" + i);
        boolean all = true;
        for (int i = 0; i < 5000; i++) all &= h.contains("w" + i);
        check(h.size() == 5000 && all && !h.contains("zzz"), "StringHashSet grow/dedupe/contains");
        StringHashSet a = WordSimilarity.uniqueWords("The cat, the DOG; a bird!");
        check(a.size() == 3 && a.contains("cat") && a.contains("dog") && a.contains("bird") && !a.contains("the"), "english tokenize + stop words");
        StringHashSet hi = WordSimilarity.uniqueWords("भारत की राजधानी नई दिल्ली है। भारत महान");
        check(hi.contains("भारत") && hi.contains("राजधानी") && hi.contains("दिल्ली") && hi.size() == 7, "devanagari words keep vowel signs (got " + hi.size() + ")");
        StringHashSet ta = WordSimilarity.uniqueWords("தமிழ் இந்தியாவின் மொழி. தமிழ்!");
        check(ta.contains("தமிழ்") && ta.contains("இந்தியாவின்") && ta.size() == 3, "tamil words keep pulli/vowel signs (got " + ta.size() + ")");
        StringHashSet b = WordSimilarity.uniqueWords("dog bird fish");
        String[] c = WordSimilarity.common(a, b);
        check(c.length == 2 && c[0].equals("bird") && c[1].equals("dog"), "common words sorted");
        check(WordSimilarity.uniqueWords("").size() == 0 && WordSimilarity.uniqueWords("!!! ... a").size() == 0, "empty / punctuation only");
    }

    public static int runAll() {
        checks = 0; failures = 0;
        strings(); dp(); flow(); primes(); words();
        return failures;
    }

    public static void main(String[] args) {
        int f = runAll();
        System.out.println(checks + " checks, " + f + " failures");
        System.exit(f == 0 ? 0 : 1);
    }
}
