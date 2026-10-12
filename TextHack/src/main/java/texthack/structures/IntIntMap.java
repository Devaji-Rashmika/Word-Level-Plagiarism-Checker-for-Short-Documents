package texthack.structures;

/** Hand-built open-addressing hash table (linear probing), int -> int. */
public final class IntIntMap {
    private int[] keys, vals;
    private boolean[] used;
    private int size;

    public IntIntMap() { this(4); }
    public IntIntMap(int expected) {
        int c = 8;
        while (c < expected * 2) c <<= 1;
        keys = new int[c]; vals = new int[c]; used = new boolean[c];
    }
    private int slot(int k) {
        int h = k * 0x9E3779B1;
        h ^= h >>> 15;
        return h & (keys.length - 1);
    }
    public int get(int k, int def) {
        int i = slot(k);
        while (used[i]) {
            if (keys[i] == k) return vals[i];
            i = (i + 1) & (keys.length - 1);
        }
        return def;
    }
    public void put(int k, int v) {
        if ((size + 1) * 2 > keys.length) grow();
        int i = slot(k);
        while (used[i]) {
            if (keys[i] == k) { vals[i] = v; return; }
            i = (i + 1) & (keys.length - 1);
        }
        used[i] = true; keys[i] = k; vals[i] = v; size++;
    }
    private void grow() {
        int[] ok = keys, ov = vals; boolean[] ou = used;
        keys = new int[ok.length * 2]; vals = new int[ok.length * 2]; used = new boolean[ok.length * 2];
        size = 0;
        for (int i = 0; i < ok.length; i++) if (ou[i]) put(ok[i], ov[i]);
    }
    public int size() { return size; }
    // slot iteration (for BFS over trie children)
    public int capacity() { return keys.length; }
    public boolean usedAt(int i) { return used[i]; }
    public int keyAt(int i) { return keys[i]; }
    public int valAt(int i) { return vals[i]; }
}
