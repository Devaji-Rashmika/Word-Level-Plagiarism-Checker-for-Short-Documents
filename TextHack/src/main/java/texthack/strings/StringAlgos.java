package texthack.strings;

import texthack.structures.IntList;

/**
 * String algorithms over int[] code-point sequences.
 * Indices are CODE POINT indices, not UTF-16 units and not grapheme clusters
 * (a Devanagari conjunct like "क्ष" is several code points).
 * Empty patterns return no matches (documented convention).
 */
public final class StringAlgos {
    private StringAlgos() {}

    /** Naive: O(nm) worst case. */
    public static IntList naive(int[] t, int[] p) {
        IntList r = new IntList();
        int n = t.length, m = p.length;
        if (m == 0 || m > n) return r;
        for (int i = 0; i + m <= n; i++) {
            int j = 0;
            while (j < m && t[i + j] == p[j]) j++;
            if (j == m) r.add(i);
        }
        return r;
    }

    public static int[] prefixFunction(int[] p) {
        int[] pi = new int[p.length];
        for (int i = 1; i < p.length; i++) {
            int k = pi[i - 1];
            while (k > 0 && p[i] != p[k]) k = pi[k - 1];
            if (p[i] == p[k]) k++;
            pi[i] = k;
        }
        return pi;
    }

    /** KMP: O(n + m). */
    public static IntList kmp(int[] t, int[] p) {
        IntList r = new IntList();
        int n = t.length, m = p.length;
        if (m == 0 || m > n) return r;
        int[] pi = prefixFunction(p);
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k > 0 && (k == m || t[i] != p[k])) k = pi[k - 1];
            if (t[i] == p[k]) k++;
            if (k == m) r.add(i - m + 1);
        }
        return r;
    }

    /** Z-function: z[i] = LCP(s, s[i..]); z[0] = n. O(n). */
    public static int[] zFunction(int[] s) {
        int n = s.length;
        int[] z = new int[n];
        for (int i = 1, l = 0, r = 0; i < n; i++) {
            if (i < r) z[i] = Math.min(r - i, z[i - l]);
            while (i + z[i] < n && s[z[i]] == s[i + z[i]]) z[i]++;
            if (i + z[i] > r) { l = i; r = i + z[i]; }
        }
        if (n > 0) z[0] = n;
        return z;
    }

    /** Pattern search via Z on p + sep + t (sep = -1 never occurs in text). O(n + m). */
    public static IntList zSearch(int[] t, int[] p) {
        IntList r = new IntList();
        int n = t.length, m = p.length;
        if (m == 0 || m > n) return r;
        int[] s = new int[m + 1 + n];
        for (int i = 0; i < m; i++) s[i] = p[i];
        s[m] = -1;
        for (int i = 0; i < n; i++) s[m + 1 + i] = t[i];
        int[] z = zFunction(s);
        for (int i = m + 1; i < s.length; i++) if (z[i] >= m) r.add(i - m - 1);
        return r;
    }

    public static final class RKResult {
        public final IntList matches = new IntList();
        public int hashHits;      // windows whose double hash equalled the pattern's
        public int spuriousHits;  // hash equal but strings differ (true collisions)
    }

    /**
     * Rabin-Karp with polynomial rolling DOUBLE hash. Every hash hit is verified
     * character by character, so output is exact; collisions only cost time and are counted.
     * Expected O(n + m); worst case O(nm). Moduli must be >= 2 (tiny moduli force collisions for demos).
     */
    public static RKResult rabinKarp(int[] t, int[] p, long m1, long m2) {
        if (m1 < 2 || m2 < 2) throw new IllegalArgumentException("moduli must be >= 2");
        RKResult res = new RKResult();
        int n = t.length, m = p.length;
        if (m == 0 || m > n) return res;
        long b1 = 911382323L % m1, b2 = 972663749L % m2;
        long pw1 = 1, pw2 = 1;
        for (int i = 0; i < m - 1; i++) { pw1 = pw1 * b1 % m1; pw2 = pw2 * b2 % m2; }
        long hp1 = 0, hp2 = 0, ht1 = 0, ht2 = 0;
        for (int i = 0; i < m; i++) {
            hp1 = (hp1 * b1 + p[i]) % m1; hp2 = (hp2 * b2 + p[i]) % m2;
            ht1 = (ht1 * b1 + t[i]) % m1; ht2 = (ht2 * b2 + t[i]) % m2;
        }
        for (int i = 0; ; i++) {
            if (hp1 == ht1 && hp2 == ht2) {
                res.hashHits++;
                boolean eq = true;
                for (int j = 0; j < m && eq; j++) if (t[i + j] != p[j]) eq = false;
                if (eq) res.matches.add(i); else res.spuriousHits++;
            }
            if (i + m >= n) break;
            long d1 = ((ht1 - (t[i] % m1) * pw1 % m1) % m1 + m1) % m1;
            long d2 = ((ht2 - (t[i] % m2) * pw2 % m2) % m2 + m2) % m2;
            ht1 = (d1 * b1 + t[i + m]) % m1;
            ht2 = (d2 * b2 + t[i + m]) % m2;
        }
        return res;
    }

    /** Suffix array by prefix doubling with merge sort: O(n log^2 n). */
    public static int[] suffixArray(int[] s) {
        int n = s.length;
        int[] sa = new int[n];
        if (n == 0) return sa;
        int[] rank = new int[n], tmp = new int[n], buf = new int[n];
        for (int i = 0; i < n; i++) { sa[i] = i; rank[i] = s[i]; }
        for (int k = 1; ; k <<= 1) {
            mergeSort(sa, buf, 0, n, rank, k, n);
            tmp[sa[0]] = 0;
            for (int i = 1; i < n; i++)
                tmp[sa[i]] = tmp[sa[i - 1]] + (cmp(sa[i - 1], sa[i], rank, k, n) < 0 ? 1 : 0);
            for (int i = 0; i < n; i++) rank[i] = tmp[i];
            if (rank[sa[n - 1]] == n - 1 || k >= n) break;
        }
        return sa;
    }
    private static int cmp(int a, int b, int[] rank, int k, int n) {
        if (rank[a] != rank[b]) return rank[a] < rank[b] ? -1 : 1;
        int ra = a + k < n ? rank[a + k] : -1, rb = b + k < n ? rank[b + k] : -1;
        return ra == rb ? 0 : (ra < rb ? -1 : 1);
    }
    private static void mergeSort(int[] x, int[] buf, int lo, int hi, int[] rank, int k, int n) {
        if (hi - lo < 2) return;
        int mid = (lo + hi) >>> 1;
        mergeSort(x, buf, lo, mid, rank, k, n);
        mergeSort(x, buf, mid, hi, rank, k, n);
        int i = lo, j = mid, o = lo;
        while (i < mid && j < hi) buf[o++] = cmp(x[i], x[j], rank, k, n) <= 0 ? x[i++] : x[j++];
        while (i < mid) buf[o++] = x[i++];
        while (j < hi) buf[o++] = x[j++];
        for (int q = lo; q < hi; q++) x[q] = buf[q];
    }

    /** Kasai: lcp[i] = LCP(suffix sa[i-1], suffix sa[i]); lcp[0] = 0. O(n). */
    public static int[] kasaiLcp(int[] s, int[] sa) {
        int n = s.length;
        int[] rank = new int[n], lcp = new int[n];
        for (int i = 0; i < n; i++) rank[sa[i]] = i;
        int h = 0;
        for (int i = 0; i < n; i++) {
            if (rank[i] > 0) {
                int j = sa[rank[i] - 1];
                while (i + h < n && j + h < n && s[i + h] == s[j + h]) h++;
                lcp[rank[i]] = h;
                if (h > 0) h--;
            } else h = 0;
        }
        return lcp;
    }

    /** Longest common substring via SA + Kasai on a + [-1] + b. Returns {length, startInA, startInB}. */
    public static int[] longestCommonSubstring(int[] a, int[] b) {
        int la = a.length;
        int[] s = new int[la + 1 + b.length];
        for (int i = 0; i < la; i++) s[i] = a[i];
        s[la] = -1;
        for (int i = 0; i < b.length; i++) s[la + 1 + i] = b[i];
        int[] sa = suffixArray(s), lcp = kasaiLcp(s, sa);
        int best = 0, pa = -1, pb = -1;
        for (int i = 1; i < s.length; i++) {
            int p = sa[i - 1], q = sa[i];
            if (p == la || q == la) continue;
            if ((p < la) == (q < la)) continue;
            if (lcp[i] > best) {
                best = lcp[i];
                if (p < la) { pa = p; pb = q - la - 1; } else { pa = q; pb = p - la - 1; }
            }
        }
        return new int[]{best, pa, pb};
    }
}
