package com.abv.hrerpisapi.device;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.device.mapper.IsapiEventMapper;
import com.abv.hrerpisapi.device.model.ParsedAcsEvent;
import com.abv.hrerpisapi.service.AcsIngestService;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Connects to a device's alertStream multipart endpoint and feeds events
 * into {@link AcsIngestService}.  Runs on its own daemon thread managed by
 * {@link com.abv.hrerpisapi.service.DeviceWorkerService}.
 */
@Slf4j
public final class IsapiAlertStreamRunner implements Runnable {

    private final DeviceEntity device;
    private final AcsIngestService ingestService;
    private final IsapiEventMapper eventMapper;

    public IsapiAlertStreamRunner(DeviceEntity device,
                                   AcsIngestService ingestService,
                                   IsapiEventMapper eventMapper) {
        this.device = device;
        this.ingestService = ingestService;
        this.eventMapper = eventMapper;
    }

    @Override
    public void run() {
        int backoffSeconds = 3;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                runOnce();
                backoffSeconds = 3;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("alertStream crashed for device {} retry in {}s: {}",
                        device.getId(), backoffSeconds, e.getMessage(), e);
                try {
                    Thread.sleep(backoffSeconds * 1000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoffSeconds = Math.min(backoffSeconds * 2, 60);
            }
        }
    }

    private void runOnce() throws Exception {
        String url = "http://" + device.getIp() + "/ISAPI/Event/notification/alertStream";

        Process p = new ProcessBuilder(
                "curl",
                "-N",
                "--digest",
                "-u", device.getUsername() + ":" + device.getPassword(),
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
                skipOptionalCrlf(in);

                int first = firstNonWhitespaceByte(body);
                if (first < 0) continue;
                byte b = body[first];
                if (b != '{' && b != '[') continue;

                String json = new String(body, StandardCharsets.UTF_8).trim();
                try {
                    handleJson(json);
                } catch (Exception parseEx) {
                    log.warn("alertStream parse error for device {} skipping part: {}",
                            device.getId(), parseEx.getMessage(), parseEx);
                }
            }
        } finally {
            p.destroyForcibly();
        }
    }

    private void handleJson(String json) throws Exception {
        ParsedAcsEvent event = eventMapper.map(json);
        if (event == null) return;
        ingestService.ingest(device, event);
    }

    // -----------------------------------------------------------------------
    // Multipart parsing helpers
    // -----------------------------------------------------------------------

    private static int readHeadersAndGetContentLength(InputStream in) throws IOException {
        int contentLength = -1;

        while (true) {
            String line = readAsciiLine(in);
            if (line == null) throw new EOFException("Stream ended");

            line = line.trim();
            if (line.isEmpty()) {
                if (contentLength > 0) return contentLength;
                continue;
            }

            if (line.startsWith("--")) continue;

            if (line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
                String v = line.substring("Content-Length:".length()).trim();
                try {
                    contentLength = Integer.parseInt(v);
                } catch (NumberFormatException ignore) {
                }
            }
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
}
