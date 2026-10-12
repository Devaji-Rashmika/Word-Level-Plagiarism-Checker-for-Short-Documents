package texthack.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Corpus layer (java.util allowed here, outside the algorithm engine).
 * Documents are NFC-normalized; search text is lower-cased per code point so offsets match display text.
 */
public final class Corpus {
    public static final class Doc {
        public String id, title = "", lang = "", source = "UNLABELED";
        public int[] display;   // NFC code points, for output
        public int[] search;    // same length, per-code-point lower-case
    }
    public static final class Cite { public String from, to; public int cap; }

    public final List<Doc> docs = new ArrayList<>();
    public final List<Cite> citations = new ArrayList<>();
    public String citationLabel = "", citationSource = "", citationSink = "";

    public static int[] normalizeForSearch(String s) {
        int[] cps = Normalizer.normalize(s, Normalizer.Form.NFC).codePoints().toArray();
        for (int i = 0; i < cps.length; i++) cps[i] = Character.toLowerCase(cps[i]);
        return cps;
    }

    public Doc find(String id) {
        for (Doc d : docs) if (d.id.equals(id)) return d;
        return null;
    }

    public static Corpus load(Path root) throws IOException {
        Corpus c = new Corpus();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root.resolve("sample"), "*.txt")) {
            for (Path p : ds) files.add(p);
        }
        Collections.sort(files);
        for (Path p : files) {
            Doc d = new Doc();
            String name = p.getFileName().toString();
            d.id = name.substring(0, name.length() - 4);
            StringBuilder body = new StringBuilder();
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (line.startsWith("# title:")) d.title = line.substring(8).trim();
                else if (line.startsWith("# lang:")) d.lang = line.substring(7).trim();
                else if (line.startsWith("# source:")) d.source = line.substring(9).trim();
                else { if (body.length() > 0) body.append('\n'); body.append(line); }
            }
            String text = Normalizer.normalize(body.toString().trim(), Normalizer.Form.NFC);
            d.display = text.codePoints().toArray();
            d.search = new int[d.display.length];
            for (int i = 0; i < d.display.length; i++) d.search[i] = Character.toLowerCase(d.display[i]);
            c.docs.add(d);
        }
        Path cite = root.resolve("metadata").resolve("citations.tsv");
        if (Files.exists(cite)) {
            for (String line : Files.readAllLines(cite, StandardCharsets.UTF_8)) {
                if (line.startsWith("#label ")) c.citationLabel = line.substring(7).trim();
                else if (line.startsWith("#source ")) c.citationSource = line.substring(8).trim();
                else if (line.startsWith("#sink ")) c.citationSink = line.substring(6).trim();
                else if (!line.isBlank() && !line.startsWith("#")) {
                    String[] f = line.split("\t");
                    if (f.length == 3) {
                        Cite x = new Cite();
                        x.from = f[0]; x.to = f[1]; x.cap = Integer.parseInt(f[2].trim());
                        c.citations.add(x);
                    }
                }
            }
        }
        return c;
    }
}
