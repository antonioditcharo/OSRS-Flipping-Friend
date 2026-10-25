package com.flippingfriend.core;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;

/** OS-owned lifetime fence. Process exit releases ownership; no wall-clock expiry grants takeover. */
public final class AccountWriterLease implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private AccountWriterLease(FileChannel channel, FileLock lock) { this.channel=channel;this.lock=lock; }
    public static AccountWriterLease tryAcquire(Path directory) throws IOException {
        Files.createDirectories(directory);
        FileChannel channel=FileChannel.open(directory.resolve("capture-writer.lock"),
            StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try {
            FileLock lock=channel.tryLock();
            if(lock!=null)return new AccountWriterLease(channel,lock);
        }catch(OverlappingFileLockException busy) {
            // A second plugin instance in this JVM has the same exclusion as another process.
        }catch(IOException|RuntimeException error) {channel.close();throw error;}
        channel.close();return null;
    }
    @Override public void close() throws IOException { try {lock.release();}finally{channel.close();} }
}
