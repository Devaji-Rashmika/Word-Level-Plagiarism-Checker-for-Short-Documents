package texthack.structures;

/** Hand-built growable int array (no Java collections). */
public final class IntList {
    private int[] a = new int[8];
    private int n;

    public void add(int v) {
        if (n == a.length) {
            int[] b = new int[n * 2];
            for (int i = 0; i < n; i++) b[i] = a[i];
            a = b;
        }
        a[n++] = v;
    }
    public int get(int i) {
        if (i < 0 || i >= n) throw new IndexOutOfBoundsException("index " + i + " size " + n);
        return a[i];
    }
    public void set(int i, int v) {
        if (i < 0 || i >= n) throw new IndexOutOfBoundsException("index " + i + " size " + n);
        a[i] = v;
    }
    public int size() { return n; }
    public int[] toArray() {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = a[i];
        return r;
    }
}
