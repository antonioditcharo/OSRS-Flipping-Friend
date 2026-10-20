package com.flippingfriend;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.FileAppender;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;

/** Synthetic trades and timings must never enter the player's RuneLite log. */
public class TestLogIsolationTest {
    @Test public void testLoggingHasNoPlayerFileAppender() {
        var context=(LoggerContext)LoggerFactory.getILoggerFactory();int appenders=0;
        for(var logger:context.getLoggerList()) {
            var iterator=logger.iteratorForAppenders();
            while(iterator.hasNext()) {
                var appender=iterator.next();appenders++;
                assertFalse("Test logging must not write any player file: "+appender.getName(),appender instanceof FileAppender);
            }
        }
        assertTrue("Keep console diagnostics available",appenders>0);
    }
}
