package texthack.strings;

import texthack.structures.StringHashSet;

/**
 * Word-level document overlap with hash sets.
 * Tokenizer is Unicode-aware: letters, digits and combining marks (Devanagari/Tamil vowel signs) are kept,
 * everything else separates words. Case-folded per code point. Not a linguistic segmenter: no stemming,
 * no handling of scripts written without spaces.
 */
public final class WordSimilarity {
    private WordSimilarity() {}

    private static final String[] STOP = {"the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "be", "been",
        "of", "to", "in", "on", "for", "with", "as", "at", "by", "it", "this", "that", "these", "those", "from", "which"};

    private static boolean isStop(String w) {
        for (int i = 0; i < STOP.length; i++) if (STOP[i].equals(w)) return true;
        return false;
    }

    /** Unique words (length >= 2 code points, English stop words removed). O(n) expected. */
    public static StringHashSet uniqueWords(String text) {
        StringHashSet set = new StringHashSet();
        StringBuilder cur = new StringBuilder();
        int curCps = 0;
        int i = 0, n = text.length();
        while (i <= n) {
            int cp = i < n ? text.codePointAt(i) : -1;
            boolean keep = false;
            if (cp >= 0) {
                int t = Character.getType(cp);
                keep = Character.isLetterOrDigit(cp) || cp == '\''
                    || t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK;
            }
            if (keep) {
                cur.appendCodePoint(Character.toLowerCase(cp));
                curCps++;
            } else if (curCps > 0) {
                String w = cur.toString();
                if (curCps > 1 && !isStop(w)) set.add(w);
                cur.setLength(0);
                curCps = 0;
            }
            if (cp < 0) break;
            i += Character.charCount(cp);
        }
        return set;
    }

    /** Words present in both sets, sorted (merge sort). O(|A| + |B| log) . */
    public static String[] common(StringHashSet a, StringHashSet b) {
        String[] aw = a.toArray();
        int cnt = 0;
        String[] tmp = new String[aw.length];
        for (int i = 0; i < aw.length; i++) if (b.contains(aw[i])) tmp[cnt++] = aw[i];
        String[] r = new String[cnt];
        for (int i = 0; i < cnt; i++) r[i] = tmp[i];
        sort(r, new String[cnt], 0, cnt);
        return r;
    }

    private static void sort(String[] x, String[] buf, int lo, int hi) {
        if (hi - lo < 2) return;
        int mid = (lo + hi) >>> 1;
        sort(x, buf, lo, mid);
        sort(x, buf, mid, hi);
        int i = lo, j = mid, o = lo;
        while (i < mid && j < hi) buf[o++] = x[i].compareTo(x[j]) <= 0 ? x[i++] : x[j++];
        while (i < mid) buf[o++] = x[i++];
        while (j < hi) buf[o++] = x[j++];
        for (int q = lo; q < hi; q++) x[q] = buf[q];
    }
}
