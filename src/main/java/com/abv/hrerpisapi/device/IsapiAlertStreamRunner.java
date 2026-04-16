package com.abv.hrerpisapi.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Set;

public final class IsapiAlertStreamRunner implements Runnable {
    private static final ObjectMapper om = new ObjectMapper();

    private final String ip;
    private final String username;
    private final String password;
    private final int deviceId;

    // MVP allowlist (sənin real cihazında təsdiqlənib)
    private final Set<Integer> successMinors = Set.of(1, 75); // card=0x01, face=0x4B

    public IsapiAlertStreamRunner(String ip, String username, String password, int deviceId) {
        this.ip = ip;
        this.username = username;
        this.password = password;
        this.deviceId = deviceId;
    }

    @Override
    public void run() {
        int backoffSeconds = 3;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                runOnce();
                // normal çıxsa (çox nadir), backoff reset
                backoffSeconds = 3;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                System.err.println("alertStream crashed, will retry in " + backoffSeconds + "s: " + e.getMessage());
                try {
                    sleepSeconds(backoffSeconds);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                backoffSeconds = Math.min(backoffSeconds * 2, 60);
            }
        }
    }

    private void runOnce() throws Exception {
        String url = "http://" + ip + "/ISAPI/Event/notification/alertStream";

        // cmd.exe istifadə ETMƏ: encoding/quote problemləri yaradır
        Process p = new ProcessBuilder(
                "curl",
                "-N",
                "--digest",
                "-u", username + ":" + password,
                "-H", "Accept: multipart/x-mixed-replace",
                url
        )
                .redirectErrorStream(true)
                .start();

        try (InputStream raw = p.getInputStream();
             BufferedInputStream in = new BufferedInputStream(raw)) {

            while (true) {
                int contentLength = readHeadersAndGetContentLength(in);
                if (contentLength <= 0) continue;

                byte[] body = readFully(in, contentLength);

                // adətən part body-dən sonra CRLF gəlir
                skipOptionalCrlf(in);

                // JSON deyil isə sakitcə skip elə
                int first = firstNonWhitespaceByte(body);
                if (first < 0) continue; // boş body
                byte b = body[first];
                if (b != '{' && b != '[') {
                    continue;
                }

                String json = new String(body, StandardCharsets.UTF_8).trim();

                try {
                    handleJson(json);
                } catch (Exception parseEx) {
                    // stream-i öldürmürük, bir part-ı buraxırıq
                    System.err.println("JSON parse failed, skipping one part: " + parseEx.getMessage());
                    // debug üçün lazımdırsa aç:
                    // System.err.println(json);
                }
            }
        } finally {
            // prosesin özü də bəzən “öz-özünə” bağlanır; biz yenidən qoşulacağıq
            p.destroyForcibly();
        }
    }

    private static void sleepSeconds(int seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000L);
    }

    /**
     * Multipart part header-lərini oxuyur.
     * Boundary sətrlərini və boş sətrləri keçir, Content-Length tapanda qaytarır.
     */
    private static int readHeadersAndGetContentLength(InputStream in) throws IOException {
        int contentLength = -1;

        while (true) {
            String line = readAsciiLine(in);
            if (line == null) throw new EOFException("Stream ended");

            line = line.trim();
            if (line.isEmpty()) {
                // header-lər bitdi (boş sətir)
                if (contentLength > 0) return contentLength;
                // boş sətir amma content-length yoxdursa -> boundary arası ola bilər, davam et
                continue;
            }

            // boundary: --MIME_boundary
            if (line.startsWith("--")) continue;

            if (line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
                String v = line.substring("Content-Length:".length()).trim();
                try { contentLength = Integer.parseInt(v); } catch (Exception ignore) {}
            }

            // başqa header-lər (Content-Type və s.) ignore
        }
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) throw new EOFException("Unexpected end of stream while reading body");
            off += r;
        }
        return buf;
    }

    private static void skipOptionalCrlf(BufferedInputStream in) throws IOException {
        in.mark(2);
        int a = in.read();
        int b = in.read();
        if (a == '\r' && b == '\n') return;
        in.reset();
    }

    private static int firstNonWhitespaceByte(byte[] body) {
        for (int i = 0; i < body.length; i++) {
            byte b = body[i];
            if (b != ' ' && b != '\r' && b != '\n' && b != '\t') return i;
        }
        return -1;
    }

    /**
     * alertStream header-ləri ASCII-dir. \n-ə qədər oxuyuruq, \r-ni atırıq.
     */
    private static String readAsciiLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (baos.size() == 0) return null;
                break;
            }
            if (ch == '\n') break;
            if (ch != '\r') baos.write(ch);
        }
        return baos.toString(StandardCharsets.US_ASCII);
    }

    private void handleJson(String json) throws Exception {
        JsonNode root = om.readTree(json);
        if (!"AccessControllerEvent".equals(root.path("eventType").asText())) return;

        String dt = root.path("dateTime").asText(null);
        OffsetDateTime time = dt == null ? null : OffsetDateTime.parse(dt);

        JsonNode ace = root.get("AccessControllerEvent");
        if (ace == null || ace.isNull()) return;

        int major = ace.path("majorEventType").asInt(-1);
        int minor = ace.path("subEventType").asInt(-1);
        long serialNo = ace.path("serialNo").asLong(-1);

        String employeeNo = ace.path("employeeNoString").asText("");
        String cardNo = ace.path("cardNo").asText("");

        String identity;
        if (!employeeNo.isBlank()) identity = "E:" + employeeNo;
        else if (!cardNo.isBlank()) identity = "C:" + cardNo;
        else identity = "U:" + serialNo;

        boolean success = (major == 5 && successMinors.contains(minor));

        if (success) {
            System.out.printf(
                    "RAW saved: deviceId=%d t=%s major=%d(0x%x) minor=%d(0x%x) success=true identity=%s serialNo=%d%n",
                    deviceId,
                    time == null ? "" : time,
                    major, major,
                    minor, minor,
                    identity,
                    serialNo
            );
        }
    }
}