package texthack.structures;

/** Hand-built hash set of Strings (open addressing, linear probing, load factor <= 1/2). */
public final class StringHashSet {
    private String[] table = new String[16];
    private int size;

    private int slot(String s, int len) {
        int h = s.hashCode();
        h ^= h >>> 16;
        return h & (len - 1);
    }
    public boolean contains(String s) {
        int i = slot(s, table.length);
        while (table[i] != null) {
            if (table[i].equals(s)) return true;
            i = (i + 1) & (table.length - 1);
        }
        return false;
    }
    /** Returns true if newly added. */
    public boolean add(String s) {
        if ((size + 1) * 2 > table.length) grow();
        int i = slot(s, table.length);
        while (table[i] != null) {
            if (table[i].equals(s)) return false;
            i = (i + 1) & (table.length - 1);
        }
        table[i] = s;
        size++;
        return true;
    }
    private void grow() {
        String[] old = table;
        table = new String[old.length * 2];
        size = 0;
        for (int i = 0; i < old.length; i++) if (old[i] != null) add(old[i]);
    }
    public int size() { return size; }
    public String[] toArray() {
        String[] r = new String[size];
        int k = 0;
        for (int i = 0; i < table.length; i++) if (table[i] != null) r[k++] = table[i];
        return r;
    }
}
