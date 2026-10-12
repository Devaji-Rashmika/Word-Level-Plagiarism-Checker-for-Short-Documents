package texthack.flow;

import texthack.structures.IntList;

/** Directed flow network, adjacency-list (linked edge arrays); edge e and e^1 are a residual pair. */
public final class FlowNetwork {
    public final int n;
    private final int[] head;
    private final IntList to = new IntList(), cap = new IntList(), next = new IntList(), orig = new IntList();
    private int[] level, it;

    public FlowNetwork(int n) {
        if (n < 0) throw new IllegalArgumentException("n");
        this.n = n;
        head = new int[n];
        for (int i = 0; i < n; i++) head[i] = -1;
    }

    /** Returns the forward edge id (even). Capacity 0 is allowed. */
    public int addEdge(int u, int v, int c) {
        if (u < 0 || u >= n || v < 0 || v >= n) throw new IllegalArgumentException("vertex out of range");
        if (c < 0) throw new IllegalArgumentException("negative capacity");
        int id = to.size();
        to.add(v); cap.add(c); orig.add(c); next.add(head[u]); head[u] = id;
        to.add(u); cap.add(0); orig.add(0); next.add(head[v]); head[v] = id + 1;
        return id;
    }
    public int edgeSlots() { return to.size(); }
    public int from(int e) { return to.get(e ^ 1); }
    public int to(int e) { return to.get(e); }
    public int capacity(int e) { return orig.get(e); }
    public int flow(int e) { return orig.get(e) - cap.get(e); }   // forward edges only
    public void reset() { for (int e = 0; e < cap.size(); e++) cap.set(e, orig.get(e)); }

    /**
     * Edmonds-Karp (BFS augmenting paths): O(V E^2). trace gets, per augmentation:
     * pathLength, v0..vk, bottleneck.
     */
    public long edmondsKarp(int s, int t, IntList trace) {
        check(s, t);
        reset();
        long flow = 0;
        int[] par = new int[n];
        while (true) {
            for (int i = 0; i < n; i++) par[i] = -1;
            par[s] = -2;
            IntList q = new IntList();
            q.add(s);
            int qh = 0;
            while (qh < q.size() && par[t] == -1) {
                int u = q.get(qh++);
                for (int e = head[u]; e != -1; e = next.get(e)) {
                    int v = to.get(e);
                    if (cap.get(e) > 0 && par[v] == -1) { par[v] = e; q.add(v); }
                }
            }
            if (par[t] == -1) break;
            int b = Integer.MAX_VALUE, cnt = 1;
            for (int v = t; v != s; v = to.get(par[v] ^ 1)) { b = Math.min(b, cap.get(par[v])); cnt++; }
            int[] path = new int[cnt];
            int k = cnt - 1;
            for (int v = t; ; v = to.get(par[v] ^ 1)) {
                path[k--] = v;
                if (v == s) break;
                int e = par[v];
                cap.set(e, cap.get(e) - b);
                cap.set(e ^ 1, cap.get(e ^ 1) + b);
            }
            if (trace != null) {
                trace.add(cnt);
                for (int i = 0; i < cnt; i++) trace.add(path[i]);
                trace.add(b);
            }
            flow += b;
        }
        return flow;
    }

    /** Dinic: O(V^2 E) general. phasesOut[0] receives the number of level-graph phases. */
    public long dinic(int s, int t, int[] phasesOut) {
        check(s, t);
        reset();
        long total = 0;
        int phases = 0;
        level = new int[n];
        it = new int[n];
        while (buildLevels(s, t)) {
            phases++;
            for (int i = 0; i < n; i++) it[i] = head[i];
            int f;
            while ((f = blocking(s, t, Integer.MAX_VALUE)) > 0) total += f;
        }
        if (phasesOut != null && phasesOut.length > 0) phasesOut[0] = phases;
        return total;
    }
    private boolean buildLevels(int s, int t) {
        for (int i = 0; i < n; i++) level[i] = -1;
        level[s] = 0;
        IntList q = new IntList();
        q.add(s);
        int qh = 0;
        while (qh < q.size()) {
            int u = q.get(qh++);
            for (int e = head[u]; e != -1; e = next.get(e)) {
                int v = to.get(e);
                if (cap.get(e) > 0 && level[v] < 0) { level[v] = level[u] + 1; q.add(v); }
            }
        }
        return level[t] >= 0;
    }
    private int blocking(int u, int t, int f) {
        if (u == t) return f;
        for (; it[u] != -1; it[u] = next.get(it[u])) {
            int e = it[u], v = to.get(e);
            if (cap.get(e) > 0 && level[v] == level[u] + 1) {
                int d = blocking(v, t, Math.min(f, cap.get(e)));
                if (d > 0) {
                    cap.set(e, cap.get(e) - d);
                    cap.set(e ^ 1, cap.get(e ^ 1) + d);
                    return d;
                }
            }
        }
        return 0;
    }

    /** After a max-flow run: vertices reachable from s in the residual graph (the source side of a min cut). */
    public boolean[] minCutSourceSide(int s) {
        boolean[] seen = new boolean[n];
        IntList q = new IntList();
        q.add(s); seen[s] = true;
        int qh = 0;
        while (qh < q.size()) {
            int u = q.get(qh++);
            for (int e = head[u]; e != -1; e = next.get(e)) {
                int v = to.get(e);
                if (cap.get(e) > 0 && !seen[v]) { seen[v] = true; q.add(v); }
            }
        }
        return seen;
    }
    private void check(int s, int t) {
        if (s < 0 || s >= n || t < 0 || t >= n) throw new IllegalArgumentException("source/sink out of range");
        if (s == t) throw new IllegalArgumentException("source equals sink");
    }
}
