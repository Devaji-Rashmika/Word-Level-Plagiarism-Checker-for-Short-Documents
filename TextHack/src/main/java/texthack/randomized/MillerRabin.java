package texthack.randomized;

import java.math.BigInteger;

/**
 * Miller-Rabin over BigInteger (java.math only). Modular exponentiation is hand-written
 * square-and-multiply on exact integers; no floating point anywhere.
 * For n < 3,317,044,064,679,887,385,961,981 the first 13 primes as bases are a DETERMINISTIC proof.
 * Above that, random bases are used and a composite survives each round with probability <= 1/4.
 */
public final class MillerRabin {
    private MillerRabin() {}

    private static final int[] SMALL = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41};
    private static final BigInteger DET_LIMIT = new BigInteger("3317044064679887385961981");

    public static final class Result {
        public String verdict;        // COMPOSITE | PRIME | PROBABLE_PRIME | NEITHER
        public boolean deterministic;
        public int rounds;
        public String witness = "";   // base proving compositeness, or small divisor
        public String errorBound = "";
        public String note = "";
    }

    public static BigInteger modPow(BigInteger base, BigInteger exp, BigInteger mod) {
        BigInteger result = BigInteger.ONE.mod(mod), b = base.mod(mod);
        for (int i = exp.bitLength() - 1; i >= 0; i--) {
            result = result.multiply(result).mod(mod);
            if (exp.testBit(i)) result = result.multiply(b).mod(mod);
        }
        return result;
    }

    /** true if 'a' proves n composite. n odd, > 3; n-1 = d * 2^s. */
    private static boolean isWitness(BigInteger a, BigInteger n, BigInteger d, int s) {
        BigInteger nm1 = n.subtract(BigInteger.ONE);
        BigInteger x = modPow(a, d, n);
        if (x.equals(BigInteger.ONE) || x.equals(nm1)) return false;
        for (int r = 1; r < s; r++) {
            x = x.multiply(x).mod(n);
            if (x.equals(nm1)) return false;
        }
        return true;
    }

    public static Result test(BigInteger n, int randomRounds, long seed) {
        Result res = new Result();
        if (n.compareTo(BigInteger.TWO) < 0) {
            res.verdict = "NEITHER"; res.deterministic = true; res.note = "n < 2 is neither prime nor composite";
            return res;
        }
        for (int p : SMALL) {
            BigInteger bp = BigInteger.valueOf(p);
            if (n.equals(bp)) { res.verdict = "PRIME"; res.deterministic = true; res.note = "n is a small prime"; return res; }
            if (n.mod(bp).signum() == 0) {
                res.verdict = "COMPOSITE"; res.deterministic = true; res.witness = "divisible by " + p;
                return res;
            }
        }
        BigInteger nm1 = n.subtract(BigInteger.ONE), d = nm1;
        int s = 0;
        while (!d.testBit(0)) { d = d.shiftRight(1); s++; }
        if (n.compareTo(DET_LIMIT) < 0) {
            for (int p : SMALL) {
                res.rounds++;
                if (isWitness(BigInteger.valueOf(p), n, d, s)) {
                    res.verdict = "COMPOSITE"; res.deterministic = true; res.witness = "base " + p;
                    return res;
                }
            }
            res.verdict = "PRIME"; res.deterministic = true;
            res.note = "Deterministic: bases 2..41 are sufficient for n < 3.317e24";
            return res;
        }
        long st = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        BigInteger range = n.subtract(BigInteger.valueOf(3));  // bases in [2, n-2]
        for (int r = 0; r < randomRounds; r++) {
            res.rounds++;
            byte[] bytes = new byte[n.bitLength() / 8 + 9];
            for (int i = 0; i < bytes.length; i++) {
                st ^= st << 13; st ^= st >>> 7; st ^= st << 17;   // xorshift64
                bytes[i] = (byte) st;
            }
            BigInteger a = new BigInteger(1, bytes).mod(range).add(BigInteger.TWO);
            if (isWitness(a, n, d, s)) {
                res.verdict = "COMPOSITE"; res.deterministic = true; res.witness = "base " + a;
                return res;
            }
        }
        res.verdict = "PROBABLE_PRIME"; res.deterministic = false;
        res.errorBound = "<= 4^-" + randomRounds + " for a composite n (for worst-case n, assuming independent uniform bases)";
        res.note = "Not a proof of primality";
        return res;
    }
}
