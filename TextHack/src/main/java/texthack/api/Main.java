package texthack.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import texthack.corpus.Corpus;

/** Entry point: java -cp ... texthack.api.Main [port] [frontendDir] [corpusDir] */
public final class Main {
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path web = Paths.get(args.length > 1 ? args[1] : "frontend").toAbsolutePath().normalize();
        Path cdir = Paths.get(args.length > 2 ? args[2] : "corpus");
        Corpus corpus = Corpus.load(cdir);
        QueryService svc = new QueryService(corpus);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        server.createContext("/api/health", ex -> json(ex, 200,
            "{\"ok\":true,\"status\":\"up\",\"documents\":" + corpus.docs.size() + "}"));
        server.createContext("/api/algorithms", ex -> json(ex, 200, QueryService.algorithms()));
        server.createContext("/api/query", ex -> {
            if (!"POST".equals(ex.getRequestMethod())) { json(ex, 405, QueryService.err("Use POST with {\"command\": \"...\"}")); return; }
            byte[] body;
            try (InputStream in = ex.getRequestBody()) { body = in.readNBytes(8193); }
            if (body.length > 8192) { json(ex, 413, QueryService.err("Request body too large.")); return; }
            String cmd = field(new String(body, StandardCharsets.UTF_8), "command");
            if (cmd == null) { json(ex, 400, QueryService.err("Missing string field \"command\".")); return; }
            String out = svc.execute(cmd);
            json(ex, out.startsWith("{\"ok\":false") ? 400 : 200, out);
        });
        server.createContext("/api/similarity", ex -> {
            if (!"POST".equals(ex.getRequestMethod())) { json(ex, 405, QueryService.err("Use POST with {\"names\":[...],\"texts\":[...]}")); return; }
            byte[] body;
            try (InputStream in = ex.getRequestBody()) { body = in.readNBytes(12_000_001); }
            if (body.length > 12_000_000) { json(ex, 413, QueryService.err("Request body too large (max 12 MB).")); return; }
            String text = new String(body, StandardCharsets.UTF_8);
            String[] names = stringArray(text, "names"), texts = stringArray(text, "texts");
            if (names == null || texts == null) { json(ex, 400, QueryService.err("Body must contain string arrays \"names\" and \"texts\".")); return; }
            String out = QueryService.similarity(names, texts);
            json(ex, out.startsWith("{\"ok\":false") ? 400 : 200, out);
        });
        server.createContext("/", ex -> {
            String p = ex.getRequestURI().getPath();
            if (p.equals("/")) p = "/index.html";
            Path f = web.resolve(p.substring(1)).normalize();
            if (!f.startsWith(web) || !Files.isRegularFile(f)) { json(ex, 404, QueryService.err("Not found")); return; }
            String n = f.getFileName().toString();
            String type = n.endsWith(".html") ? "text/html; charset=utf-8" : n.endsWith(".css") ? "text/css; charset=utf-8"
                : n.endsWith(".js") ? "text/javascript; charset=utf-8" : "application/octet-stream";
            send(ex, 200, type, Files.readAllBytes(f));
        });
        server.start();
        System.out.println("TextHack running at http://127.0.0.1:" + port + "/  (" + corpus.docs.size() + " sample documents)");
    }

    static void json(HttpExchange ex, int code, String s) throws IOException {
        send(ex, code, "application/json; charset=utf-8", s.getBytes(StandardCharsets.UTF_8));
    }
    static void send(HttpExchange ex, int code, String type, byte[] b) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }

    /** Index of the first value character after "name": , or -1. */
    static int valueStart(String json, String name) {
        int k = json.indexOf("\"" + name + "\"");
        if (k < 0) return -1;
        int i = json.indexOf(':', k);
        if (i < 0) return -1;
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        return i < json.length() ? i : -1;
    }

    /** Decodes the JSON string literal starting at json.charAt(i) == '"'; returns the index after the closing quote, or -1. */
    static int parseStr(String json, int i, StringBuilder sb) {
        for (i++; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') return i + 1;
            if (c == '\\' && i + 1 < json.length()) {
                char d = json.charAt(++i);
                switch (d) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 >= json.length()) return -1;
                        try { sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16)); } catch (NumberFormatException e) { return -1; }
                        i += 4; break;
                    default: sb.append(d);
                }
            } else sb.append(c);
        }
        return -1;
    }

    /** Top-level string field of a flat JSON object, or null. */
    static String field(String json, String name) {
        int i = valueStart(json, name);
        if (i < 0 || json.charAt(i) != '"') return null;
        StringBuilder sb = new StringBuilder();
        return parseStr(json, i, sb) < 0 ? null : sb.toString();
    }

    /** Top-level array of strings, or null if absent or malformed. */
    static String[] stringArray(String json, String name) {
        int i = valueStart(json, name);
        if (i < 0 || json.charAt(i) != '[') return null;
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        i++;
        while (true) {
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) return null;
            if (json.charAt(i) == ']') break;
            if (json.charAt(i) != '"') return null;
            StringBuilder sb = new StringBuilder();
            i = parseStr(json, i, sb);
            if (i < 0) return null;
            out.add(sb.toString());
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i < json.length() && json.charAt(i) == ',') { i++; continue; }
            if (i < json.length() && json.charAt(i) == ']') break;
            return null;
        }
        return out.toArray(new String[0]);
    }
}
