package com.abv.hrerpisapi.device;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class IsapiAlertStreamRunnerTest {

    @Test
    void parseResponseStatusXmlShouldDetectDeployExceedMax() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ResponseStatus version="2.0" xmlns="http://www.isapi.org/ver20/XMLSchema">
                    <statusValue>503</statusValue>
                    <subStatusCode>deployExceedMax</subStatusCode>
                    <errorMsg>deployExceedMax</errorMsg>
                </ResponseStatus>
                """;

        IsapiAlertStreamRunner.ResponseStatusDetails details = IsapiAlertStreamRunner.parseResponseStatusXml(xml);

        assertNotNull(details);
        assertEquals(503, details.statusCode());
        assertEquals("deployExceedMax", details.subStatusCode());
        assertTrue(details.deployExceedMax());
    }

    @Test
    void parseResponseStatusXmlShouldParseUnauthorizedUserCheck() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <userCheck version="2.0" xmlns="http://www.isapi.org/ver20/XMLSchema">
                    <statusValue>401</statusValue>
                    <statusString>Unauthorized</statusString>
                    <retryLoginTime>2</retryLoginTime>
                </userCheck>
                """;

        IsapiAlertStreamRunner.ResponseStatusDetails details = IsapiAlertStreamRunner.parseResponseStatusXml(xml);

        assertNotNull(details);
        assertEquals(401, details.statusCode());
        assertEquals("Unauthorized", details.message());
        assertFalse(details.deployExceedMax());
    }

    @Test
    void cleanupProcessShouldCloseStreamsAndForceDestroyWhenProcessDoesNotExit() {
        TrackableInputStream input = new TrackableInputStream();
        TrackableInputStream error = new TrackableInputStream();
        StubProcess process = new StubProcess(false);

        IsapiAlertStreamRunner.cleanupProcess(process, input, error);

        assertTrue(input.closed);
        assertTrue(error.closed);
        assertEquals(1, process.destroyCalledCount);
        assertTrue(process.destroyForciblyCalled);
    }

    @Test
    void readAsciiLineShouldThrowInterruptedExceptionWhenThreadIsInterrupted() throws Exception {
        Method readAsciiLine = IsapiAlertStreamRunner.class.getDeclaredMethod("readAsciiLine", InputStream.class);
        readAsciiLine.setAccessible(true);
        Thread.currentThread().interrupt();
        try {
            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    () -> readAsciiLine.invoke(null, new ByteArrayInputStream("line\n".getBytes())));
            assertInstanceOf(InterruptedException.class, ex.getCause());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void readFullyShouldThrowInterruptedExceptionWhenThreadIsInterrupted() throws Exception {
        Method readFully = IsapiAlertStreamRunner.class.getDeclaredMethod("readFully", InputStream.class, int.class);
        readFully.setAccessible(true);
        Thread.currentThread().interrupt();
        try {
            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    () -> readFully.invoke(null, new ByteArrayInputStream("abc".getBytes()), 3));
            assertInstanceOf(InterruptedException.class, ex.getCause());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void captureCurlProcessEndDetailsShouldIncludeExitCodeAndRedactCredentials() {
        String stderr = "curl: (28) Operation timed out for admin:12345678!";
        StubProcess process = new StubProcess(true, 28);

        IsapiAlertStreamRunner.CurlProcessEndDetails details = IsapiAlertStreamRunner.captureCurlProcessEndDetails(
                process,
                new ByteArrayInputStream(stderr.getBytes()),
                "admin",
                "12345678!");

        assertEquals("28", details.exitCode());
        assertFalse(details.stderrTail().contains("12345678!"));
        assertTrue(details.stderrTail().contains("***"));
    }

    @Test
    void captureCurlProcessEndDetailsShouldSkipStderrTailWhenProcessStillRunning() {
        StubProcess process = new StubProcess(false, 0);

        IsapiAlertStreamRunner.CurlProcessEndDetails details = IsapiAlertStreamRunner.captureCurlProcessEndDetails(
                process,
                new ByteArrayInputStream("curl: pending".getBytes()),
                "admin",
                "secret");

        assertEquals("-", details.exitCode());
        assertEquals("-", details.stderrTail());
    }

    @Test
    void stopShouldBeIdempotentAndCleanupOnlyOnce() throws Exception {
        IsapiAlertStreamRunner runner = new IsapiAlertStreamRunner(device(1L), null, null, 3, 300);
        TrackableInputStream input = new TrackableInputStream();
        TrackableInputStream error = new TrackableInputStream();
        StubProcess process = new StubProcess(true, 0);

        setField(runner, "activeProcess", process);
        setField(runner, "activeInputStream", input);
        setField(runner, "activeErrorStream", error);

        runner.stop();
        runner.stop();

        assertTrue(input.closed);
        assertTrue(error.closed);
        assertEquals(1, process.destroyCalledCount);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static com.abv.hrerpisapi.dao.entity.DeviceEntity device(Long id) {
        com.abv.hrerpisapi.dao.entity.DeviceEntity d = new com.abv.hrerpisapi.dao.entity.DeviceEntity();
        d.setId(id);
        d.setIp("10.0.0.1");
        d.setUsername("admin");
        d.setPassword("secret");
        return d;
    }

    private static final class TrackableInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackableInputStream() {
            super(new byte[0]);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class StubProcess extends Process {
        private final boolean exitsWithinTimeout;
        private final int exitCode;
        private int destroyCalledCount;
        private boolean destroyForciblyCalled;

        private StubProcess(boolean exitsWithinTimeout) {
            this(exitsWithinTimeout, 0);
        }

        private StubProcess(boolean exitsWithinTimeout, int exitCode) {
            this.exitsWithinTimeout = exitsWithinTimeout;
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return exitsWithinTimeout;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyCalledCount++;
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            return this;
        }
    }
}
