package com.flippingfriend.companion;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Only process diagnostics: never account journals, events, outboxes or database files. */
final class BoundedDiagnosticLogs implements AutoCloseable
{
    static final long FILE_BYTES = 8L * 1024 * 1024;
    static final int ARCHIVES = 4;
    static final byte[] HEADER = "Flipping Friend process diagnostics v1\n".getBytes(StandardCharsets.UTF_8);
    private final PrintStream previousOut = System.out, previousErr = System.err;
    private final PrintStream out, err;

    static BoundedDiagnosticLogs install(Path root) throws IOException
    {
        BoundedDiagnosticLogs logs = new BoundedDiagnosticLogs(root.resolve("diagnostics/runtime-logs"));
        System.setOut(logs.out);
        System.setErr(logs.err);
        return logs;
    }

    private BoundedDiagnosticLogs(Path directory) throws IOException
    {
        out = new PrintStream(new Rolling(directory, "stdout.log", FILE_BYTES, ARCHIVES, previousErr),
            true, StandardCharsets.UTF_8.name());
        try
        {
            err = new PrintStream(new Rolling(directory, "stderr.log", FILE_BYTES, ARCHIVES, previousErr),
                true, StandardCharsets.UTF_8.name());
        }
        catch (IOException error) { out.close(); throw error; }
    }

    @Override public void close()
    {
        System.setOut(previousOut); System.setErr(previousErr);
        out.close(); err.close();
    }

    static void plainPath(Path path) throws IOException
    {
        Path absolute = path.toAbsolutePath().normalize();
        for (Path p = absolute; p != null; p = p.getParent())
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS) &&
                (Files.isSymbolicLink(p) || Files.readAttributes(p,
                    java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()))
                throw new IOException("Diagnostic log path is linked; refused.");
    }

    /** Splits even a single oversized write; memory and on-disk size do not depend on line length. */
    static final class Rolling extends OutputStream
    {
        private final Path file;
        private final long limit;
        private final int archives;
        private final PrintStream fallback;
        private OutputStream stream;
        private long size, dropped;
        private boolean failed;

        Rolling(Path directory, String name, long limit, int archives, PrintStream fallback) throws IOException
        {
            if (limit <= HEADER.length || archives < 1 || archives > ARCHIVES || !(name.equals("stdout.log") || name.equals("stderr.log")))
                throw new IllegalArgumentException("Bounded diagnostic policy required");
            this.file = directory.resolve(name); this.limit = limit; this.archives = archives; this.fallback = fallback;
            plainPath(directory); Files.createDirectories(directory); validate();
            size = Files.exists(file) ? Files.size(file) : 0;
            if (size > limit) throw new IOException("Oversized unmanaged diagnostic log requires preservation before startup.");
            stream = open(); size = Files.size(file);
        }

        private OutputStream open() throws IOException
        {
            plainPath(file);
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS))
            {
                try (OutputStream created = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))
                {
                    created.write(HEADER);
                }
            }
            owned(file);
            return Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS);
        }

        private void owned(Path path) throws IOException
        {
            // A regular file may be a hard link. Require our header before appending/deleting any
            // existing log; an accidentally linked database/journal is never treated as a log.
            try (java.io.InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS))
            {
                if (!java.util.Arrays.equals(HEADER, input.readNBytes(HEADER.length)))
                    throw new IOException("Unrecognized diagnostic file; preserve it before startup.");
            }
        }

        private Path archive(int index) { return file.resolveSibling(file.getFileName() + "." + index); }

        private void validate() throws IOException
        {
            for (int i = 0; i <= archives; i++)
            {
                Path p = i == 0 ? file : archive(i); plainPath(p);
                if (Files.exists(p, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Non-file diagnostic target; refused.");
                if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) owned(p);
                if (i > 0 && Files.exists(p) && Files.size(p) > limit)
                    throw new IOException("Oversized diagnostic archive requires preservation.");
            }
        }

        private void rotate() throws IOException
        {
            validate(); stream.close(); stream = null;
            Files.deleteIfExists(archive(archives));
            for (int i = archives - 1; i >= 1; i--)
                if (Files.exists(archive(i))) Files.move(archive(i), archive(i + 1), StandardCopyOption.REPLACE_EXISTING);
            Files.move(file, archive(1), StandardCopyOption.REPLACE_EXISTING);
            stream = open(); size = HEADER.length;
        }

        @Override public synchronized void write(int value) { write(new byte[] {(byte)value}, 0, 1); }

        @Override public synchronized void write(byte[] data, int offset, int length)
        {
            java.util.Objects.checkFromIndexSize(offset, length, data.length);
            if (failed) { dropped += length; return; }
            try
            {
                while (length > 0)
                {
                    if (size == limit) rotate();
                    int count = (int)Math.min(length, limit - size);
                    stream.write(data, offset, count); size += count; offset += count; length -= count;
                }
            }
            catch (IOException error)
            {
                // Disk/rotation failure must not take down financial delivery or generate a log storm.
                failed = true; dropped += length;
                if (stream != null) try { stream.close(); } catch (IOException ignored) { }
                fallback.println("Companion diagnostic logging degraded; output is being discarded until restart: " + error.getClass().getSimpleName());
            }
        }

        synchronized long droppedBytes() { return dropped; }
        @Override public synchronized void flush() throws IOException { if (!failed && stream != null) stream.flush(); }
        @Override public synchronized void close() throws IOException { failed = true; if (stream != null) { stream.close(); stream = null; } }
    }
}
