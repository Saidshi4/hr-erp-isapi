package com.abv.hrerpisapi.device;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.device.mapper.IsapiEventMapper;
import com.abv.hrerpisapi.device.model.ParsedAcsEvent;
import com.abv.hrerpisapi.service.AcsIngestService;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connects to a device's alertStream multipart endpoint and feeds events
 * into {@link AcsIngestService}.  Runs on its own daemon thread managed by
 * {@link com.abv.hrerpisapi.service.DeviceWorkerService}.
 */
@Slf4j
public final class IsapiAlertStreamRunner implements Runnable {

    private static final int DEFAULT_PORT = 80;
    private static final int MAX_DISCONNECT_BACKOFF_SECONDS = 60;
    private static final int PROCESS_STOP_TIMEOUT_SECONDS = 2;
    private static final Pattern XML_TAG_PATTERN_TEMPLATE =
            Pattern.compile("<%s(?:\\s[^>]*)?>(.*?)</%s>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final DeviceEntity device;
    private final AcsIngestService ingestService;
    private final IsapiEventMapper eventMapper;
    private final int disconnectBackoffBaseSeconds;
    private final int deployExceedMaxBackoffSeconds;

    private final Object processLock = new Object();
    private volatile Process activeProcess;
    private volatile InputStream activeInputStream;
    private volatile InputStream activeErrorStream;

    public IsapiAlertStreamRunner(DeviceEntity device,
                                   AcsIngestService ingestService,
                                   IsapiEventMapper eventMapper,
                                   int disconnectBackoffBaseSeconds,
                                   int deployExceedMaxBackoffSeconds) {
        this.device = device;
        this.ingestService = ingestService;
        this.eventMapper = eventMapper;
        this.disconnectBackoffBaseSeconds = Math.max(1, disconnectBackoffBaseSeconds);
        this.deployExceedMaxBackoffSeconds = Math.max(1, deployExceedMaxBackoffSeconds);
    }

    @Override
    public void run() {
        int backoffSeconds = disconnectBackoffBaseSeconds;
        String url = alertStreamUrl();
        int port = portFromUrl(url);

        while (!Thread.currentThread().isInterrupted()) {
            log.info("ActionLog.device.alertStream.connect.started deviceId={} ip={} port={} url={} retrySeconds={}",
                    device.getId(), device.getIp(), port, url, backoffSeconds);
            try {
                runOnce();
                backoffSeconds = disconnectBackoffBaseSeconds;
            } catch (AlertStreamResponseException ex) {
                int retrySeconds = ex.deployExceedMax()
                        ? deployExceedMaxBackoffSeconds
                        : backoffSeconds;
                log.warn("ActionLog.device.alertStream.connect.failed deviceId={} ip={} port={} statusCode={} subStatusCode={} error={} retrySeconds={} hint={}",
                        device.getId(),
                        device.getIp(),
                        port,
                        ex.statusCode(),
                        safe(ex.subStatusCode()),
                        safe(ex.getMessage()),
                        retrySeconds,
                        ex.deployExceedMax()
                                ? "device may have max subscriptions reached; restart/close stale sessions"
                                : "-");
                if (!sleepBackoff(retrySeconds)) {
                    return;
                }
                backoffSeconds = ex.deployExceedMax()
                        ? disconnectBackoffBaseSeconds
                        : Math.min(backoffSeconds * 2, MAX_DISCONNECT_BACKOFF_SECONDS);
            } catch (EOFException eof) {
                if (Thread.currentThread().isInterrupted()) {
                    stop();
                    return;
                }
                log.info("ActionLog.device.alertStream.disconnected deviceId={} ip={} port={} retrySeconds={} reason={}",
                        device.getId(), device.getIp(), port, backoffSeconds, safe(eof.getMessage()));
                if (!sleepBackoff(backoffSeconds)) {
                    return;
                }
                backoffSeconds = Math.min(backoffSeconds * 2, MAX_DISCONNECT_BACKOFF_SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                stop();
                return;
            } catch (Exception e) {
                log.warn("ActionLog.device.alertStream.connect.failed deviceId={} ip={} port={} statusCode={} subStatusCode={} error={} retrySeconds={}",
                        device.getId(), device.getIp(), port, "-", "-", safe(e.getMessage()), backoffSeconds, e);
                if (!sleepBackoff(backoffSeconds)) {
                    return;
                }
                backoffSeconds = Math.min(backoffSeconds * 2, MAX_DISCONNECT_BACKOFF_SECONDS);
            }
        }
        stop();
    }

    public void stop() {
        log.info("ActionLog.device.alertStream.stop.started deviceId={} ip={}", device.getId(), device.getIp());
        Process process;
        InputStream inputStream;
        InputStream errorStream;
        synchronized (processLock) {
            process = activeProcess;
            inputStream = activeInputStream;
            errorStream = activeErrorStream;
            activeProcess = null;
            activeInputStream = null;
            activeErrorStream = null;
        }
        cleanupProcess(process, inputStream, errorStream);
        log.info("ActionLog.device.alertStream.stop.ended deviceId={} ip={}", device.getId(), device.getIp());
    }

    private void runOnce() throws Exception {
        String url = alertStreamUrl();

        Process p = new ProcessBuilder(
                "curl",
                "-N",
                "--digest",
                "-u", device.getUsername() + ":" + device.getPassword(),
                "-H", "Accept: multipart/x-mixed-replace",
                url
        )
                .redirectErrorStream(false)
                .start();

        InputStream raw = p.getInputStream();
        InputStream error = p.getErrorStream();
        synchronized (processLock) {
            activeProcess = p;
            activeInputStream = raw;
            activeErrorStream = error;
        }

        try (BufferedInputStream in = new BufferedInputStream(raw)) {
            while (true) {
                int contentLength = readHeadersAndGetContentLength(in);
                if (contentLength <= 0) continue;

                byte[] body = readFully(in, contentLength);
                skipOptionalCrlf(in);

                int first = firstNonWhitespaceByte(body);
                if (first < 0) continue;
                byte b = body[first];
                String payload = new String(body, StandardCharsets.UTF_8).trim();
                if (b == '<') {
                    ResponseStatusDetails details = parseResponseStatusXml(payload);
                    if (details != null) {
                        throw new AlertStreamResponseException(details);
                    }
                    continue;
                }
                if (b != '{' && b != '[') continue;

                try {
                    handleJson(payload);
                } catch (Exception parseEx) {
                    log.warn("ActionLog.device.alertStream.parse.failed deviceId={} ip={} error={}",
                            device.getId(), device.getIp(), safe(parseEx.getMessage()), parseEx);
                }
            }
        } finally {
            synchronized (processLock) {
                activeProcess = null;
                activeInputStream = null;
                activeErrorStream = null;
            }
            cleanupProcess(p, raw, error);
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

    static ResponseStatusDetails parseResponseStatusXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        String statusValueRaw = extractXmlTagValue(xml, "statusValue");
        Integer statusCode = null;
        if (statusValueRaw != null) {
            try {
                statusCode = Integer.parseInt(statusValueRaw.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        String subStatusCode = extractXmlTagValue(xml, "subStatusCode");
        String errorMsg = extractXmlTagValue(xml, "errorMsg");
        String statusString = extractXmlTagValue(xml, "statusString");

        if (statusCode == null && isBlank(subStatusCode) && isBlank(errorMsg) && isBlank(statusString)) {
            return null;
        }

        String detail = firstNonBlank(errorMsg, statusString, "ISAPI ResponseStatus");
        return new ResponseStatusDetails(statusCode, subStatusCode, detail);
    }

    static void cleanupProcess(Process process, InputStream inputStream, InputStream errorStream) {
        closeQuietly(inputStream);
        closeQuietly(errorStream);
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(PROCESS_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private static String extractXmlTagValue(String xml, String tagName) {
        Pattern tagPattern = Pattern.compile(
                XML_TAG_PATTERN_TEMPLATE.pattern().formatted(tagName, tagName),
                XML_TAG_PATTERN_TEMPLATE.flags());
        Matcher matcher = tagPattern.matcher(xml);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        return value == null ? null : value.trim();
    }

    private static String safe(String value) {
        if (value == null) {
            return "-";
        }
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.isEmpty() ? "-" : cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean sleepBackoff(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            stop();
            return false;
        }
    }

    private String alertStreamUrl() {
        return "http://" + device.getIp() + "/ISAPI/Event/notification/alertStream";
    }

    private int portFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getPort() > 0 ? uri.getPort() : DEFAULT_PORT;
        } catch (Exception ignored) {
            return DEFAULT_PORT;
        }
    }

    record ResponseStatusDetails(Integer statusCode, String subStatusCode, String message) {
        boolean deployExceedMax() {
            return "deployExceedMax".equalsIgnoreCase(safe(subStatusCode))
                    || "deployExceedMax".equalsIgnoreCase(safe(message));
        }
    }

    private static final class AlertStreamResponseException extends Exception {
        private final Integer statusCode;
        private final String subStatusCode;
        private final boolean deployExceedMax;

        private AlertStreamResponseException(ResponseStatusDetails details) {
            super(details.message());
            this.statusCode = details.statusCode();
            this.subStatusCode = details.subStatusCode();
            this.deployExceedMax = details.deployExceedMax();
        }

        Integer statusCode() {
            return statusCode;
        }

        String subStatusCode() {
            return subStatusCode;
        }

        boolean deployExceedMax() {
            return deployExceedMax;
        }
    }
}
