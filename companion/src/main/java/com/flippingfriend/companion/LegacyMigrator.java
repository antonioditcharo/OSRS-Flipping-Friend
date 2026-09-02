package com.flippingfriend.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.stream.Stream;

/** Copies legacy JSON before the new service starts recording authoritative telemetry. */
final class LegacyMigrator
{
	private static final String[] LEGACY = {"offers.json", "positions.json", "buy-limits.json", "journal.jsonl", "learned-model.json"};

	static void migrate(Path root, SqliteStore store) throws Exception
	{
		Path accounts = root.resolve("accounts");
		if (!Files.isDirectory(accounts)) { return; }
		Path backupRoot = root.resolve("legacy-backup").resolve(Long.toString(Instant.now().getEpochSecond()));
		try (Stream<Path> paths = Files.walk(accounts))
		{
			paths.filter(Files::isRegularFile).filter(LegacyMigrator::isLegacy).forEach(file ->
			{
				try
				{
					String source = file.toAbsolutePath().toString();
					if (store.wasMigrated(source)) { return; }
					Path relative = accounts.relativize(file);
					Path destination = backupRoot.resolve(relative);
					Files.createDirectories(destination.getParent());
					Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
					store.recordEvent(Instant.now().getEpochSecond(), "migration", "LEGACY_IMPORT", Files.readString(file));
					store.markMigration(source, destination.toString());
				}
				catch (Exception ex) { throw new MigrationFailure(ex); }
			});
		}
		catch (MigrationFailure failure) { throw failure.getCause(); }
	}

	private static boolean isLegacy(Path file)
	{
		for (String name : LEGACY) { if (name.equals(file.getFileName().toString())) { return true; } }
		return false;
	}

	private static final class MigrationFailure extends RuntimeException
	{
		MigrationFailure(Exception cause) { super(cause); }
		@Override public Exception getCause() { return (Exception) super.getCause(); }
	}
}
