package com.abv.hrerpisapi.device;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
        assertTrue(process.destroyCalled);
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
        private boolean destroyCalled;
        private boolean destroyForciblyCalled;

        private StubProcess(boolean exitsWithinTimeout) {
            this.exitsWithinTimeout = exitsWithinTimeout;
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
            return 0;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            return this;
        }
    }
}
