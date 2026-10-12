package texthack.strings;

import texthack.structures.IntIntMap;
import texthack.structures.IntList;

/** Multi-pattern matching: O(total pattern length + n + matches). Empty patterns are ignored. */
public final class AhoCorasick {
    private final IntIntMap[] kids;
    private final int[] fail, pat, link, len;
    private int nodes = 1;

    public AhoCorasick(int[][] patterns) {
        int total = 1;
        for (int i = 0; i < patterns.length; i++) total += patterns[i].length;
        kids = new IntIntMap[total];
        fail = new int[total]; pat = new int[total]; link = new int[total]; len = new int[total];
        for (int i = 0; i < total; i++) { pat[i] = -1; link[i] = -1; }
        kids[0] = new IntIntMap();
        for (int idx = 0; idx < patterns.length; idx++) {
            int[] p = patterns[idx];
            if (p.length == 0) continue;
            int cur = 0;
            for (int c : p) {
                int nx = kids[cur].get(c, -1);
                if (nx == -1) {
                    nx = nodes++;
                    kids[nx] = new IntIntMap();
                    len[nx] = len[cur] + 1;
                    kids[cur].put(c, nx);
                }
                cur = nx;
            }
            pat[cur] = idx;
        }
        IntList q = new IntList();
        int qh = 0;
        q.add(0);
        while (qh < q.size()) {
            int u = q.get(qh++);
            IntIntMap km = kids[u];
            for (int i = 0; i < km.capacity(); i++) {
                if (!km.usedAt(i)) continue;
                int c = km.keyAt(i), v = km.valAt(i);
                if (u == 0) fail[v] = 0;
                else {
                    int f = fail[u];
                    while (f != 0 && kids[f].get(c, -1) == -1) f = fail[f];
                    int x = kids[f].get(c, -1);
                    fail[v] = (x != -1 && x != v) ? x : 0;
                }
                link[v] = pat[fail[v]] >= 0 ? fail[v] : link[fail[v]];
                q.add(v);
            }
        }
    }

    /** Returns flattened pairs: (startIndex, patternIndex, startIndex, patternIndex, ...). */
    public IntList search(int[] text) {
        IntList r = new IntList();
        int state = 0;
        for (int i = 0; i < text.length; i++) {
            int c = text[i];
            while (state != 0 && kids[state].get(c, -1) == -1) state = fail[state];
            int x = kids[state].get(c, -1);
            state = x == -1 ? 0 : x;
            for (int o = pat[state] >= 0 ? state : link[state]; o != -1; o = link[o]) {
                r.add(i - len[o] + 1);
                r.add(pat[o]);
            }
        }
        return r;
    }
}
