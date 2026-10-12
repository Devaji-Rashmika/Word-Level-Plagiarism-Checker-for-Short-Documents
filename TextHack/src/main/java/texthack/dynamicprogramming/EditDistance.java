package texthack.dynamicprogramming;

/** Wagner-Fischer Levenshtein with traceback, and Damerau (optimal string alignment). O(nm) time. */
public final class EditDistance {
    private EditDistance() {}

    public static final class Result {
        public int distance;
        public int[][] table;   // (n+1) x (m+1)
        public String ops;      // M=match S=substitute D=delete(from a) I=insert(from b)
    }

    public static Result levenshtein(int[] a, int[] b) {
        int n = a.length, m = b.length;
        int[][] t = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) t[i][0] = i;
        for (int j = 0; j <= m; j++) t[0][j] = j;
        for (int i = 1; i <= n; i++)
            for (int j = 1; j <= m; j++) {
                int c = a[i - 1] == b[j - 1] ? 0 : 1;
                t[i][j] = Math.min(Math.min(t[i - 1][j] + 1, t[i][j - 1] + 1), t[i - 1][j - 1] + c);
            }
        StringBuilder sb = new StringBuilder();
        int i = n, j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && a[i - 1] == b[j - 1] && t[i][j] == t[i - 1][j - 1]) { sb.append('M'); i--; j--; }
            else if (i > 0 && j > 0 && t[i][j] == t[i - 1][j - 1] + 1) { sb.append('S'); i--; j--; }
            else if (i > 0 && t[i][j] == t[i - 1][j] + 1) { sb.append('D'); i--; }
            else { sb.append('I'); j--; }
        }
        Result r = new Result();
        r.distance = t[n][m]; r.table = t; r.ops = sb.reverse().toString();
        return r;
    }

    /** Optimal string alignment (restricted Damerau-Levenshtein: adjacent transposition, no substring edited twice). */
    public static int damerauOsa(int[] a, int[] b) {
        int n = a.length, m = b.length;
        int[][] t = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) t[i][0] = i;
        for (int j = 0; j <= m; j++) t[0][j] = j;
        for (int i = 1; i <= n; i++)
            for (int j = 1; j <= m; j++) {
                int c = a[i - 1] == b[j - 1] ? 0 : 1;
                int v = Math.min(Math.min(t[i - 1][j] + 1, t[i][j - 1] + 1), t[i - 1][j - 1] + c);
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = Math.min(v, t[i - 2][j - 2] + 1);
                t[i][j] = v;
            }
        return t[n][m];
    }
}
