package com.flippingfriend.companion;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Starts the local-only portfolio service. It has no account credentials and no game automation. */
public final class CompanionMain
{
	private CompanionMain() { }

	/** Long enough to diagnose a bad night of ingestion, short enough to stay bounded. */
	private static final int OFFER_RETENTION_DAYS = 7;

	/** Long enough for the monitor's longest view plus room to compare against last week. */
	private static final int PLAN_RETENTION_DAYS = 30;

	/**
	 * How many versions of each model keep their weights.
	 * <p>
	 * Enough to roll back a bad promotion a few steps, which is the reason the history exists. Beyond
	 * that the weights are dead: nothing reads a model older than the newest under its name, and at
	 * 690KB apiece they were the largest thing in the database by a factor of three.
	 */
	private static final int MODEL_VERSIONS_KEPT = 5;

	/**
	 * Enough heap to plan the configured shortlist, with room for the garbage a pass makes.
	 *
	 * <p>Measured from a GC log, after guessing twice and being wrong twice. The steady state is
	 * small -- after a full collection this runs in a 197 MB heap with young pauses going 159M->78M,
	 * and there is no leak, since that collection reclaimed 672M down to 52M. What needs the room is
	 * warm-up: parsing a thousand-odd price series promotes them out of the young generation faster
	 * than G1 chooses to reclaim the old one, and young pauses were leaving 366-775 MB standing
	 * against a live set under 80 MB.
	 *
	 * <p>So the requirement is not "what does it retain" but "what does a burst promote before G1
	 * catches up", and the launchers pair this with
	 * {@code -XX:InitiatingHeapOccupancyPercent=30} so it catches up sooner.
	 */
	private static final long MINIMUM_HEAP_BYTES = 900L * 1024 * 1024;

	/**
	 * Say so at startup when the heap is too small, because the failure mode is otherwise silent.
	 *
	 * <p>The launcher pinned this at {@code -Xmx192m}, sized for a shortlist of ninety items. At six
	 * hundred the process ran out, and a JVM out of heap does not stop: it keeps the listening socket
	 * open and stops calling accept, so the port is bound, the backlog fills, and every connection is
	 * REFUSED. From the plugin that is indistinguishable from a companion that is not running, and
	 * from the player it is simply no recommendations, with nothing in any log to say why.
	 *
	 * <p>One line at startup is not a fix for that. It is the difference between a mystery and a
	 * sentence naming the flag to change.
	 */
	private static void checkHeap()
	{
		long max = Runtime.getRuntime().maxMemory();
		if (max >= MINIMUM_HEAP_BYTES)
		{
			return;
		}
		System.err.printf("This companion has %d MB of heap and needs about %d MB to study %d items. "
				+ "Start it with -Xmx1g -XX:InitiatingHeapOccupancyPercent=30. Without that it will "
				+ "run out while warming its price history, and an out-of-heap JVM holds the port open "
				+ "while refusing every connection, which looks exactly like the companion not "
				+ "running.%n",
			max / (1024 * 1024), MINIMUM_HEAP_BYTES / (1024 * 1024),
			CandidateFactory.DEEP_ANALYSIS_LIMIT);
	}

	public static void main(String[] args) throws Exception
	{
		checkHeap();

		Path root = Paths.get(System.getProperty("user.home"), ".runelite", "osrs-flipping-friend");
		Path companion = root.resolve("companion");
		Files.createDirectories(companion);
		String token = token(companion.resolve("companion.properties"));
		SqliteStore store = new SqliteStore(companion.resolve("flipping-friend.db"));
		LegacyMigrator.migrate(root, store);
		// A database from an earlier build still carries the ingestion table, which was written on
		// every tick and read by nothing -- 2.5 GB of it on this machine. Dropping rows alone does not
		// return the space, so the drop is followed by a compaction, once, while nothing else has the
		// file open. Both are no-ops on a database that has already been through this.
		if (store.dropLegacyMarketObservations())
		{
			System.out.println("Dropping the unused ingestion table and compacting the database. "
				+ "This takes a moment on a large file and happens only once.");
			store.compact();
			System.out.println("Done.");
		}
		// Offer identities the game can no longer replay. It only ever re-announces the slots that
		// are currently open, so anything this old is certain to be finished with.
		store.pruneCountedOffers(OFFER_RETENTION_DAYS);
		// The event log: mostly account snapshots, which the only query against it excludes. They are
		// worth keeping as the record of what the companion was told -- that is how several faults
		// here were eventually explained -- but not for ever. This was the last table with no bound.
		store.pruneEvents(PLAN_RETENTION_DAYS);
		// Plans are the monitor's whole history view, so they are kept far longer than ingestion rows
		// -- but they are kept, not hoarded.
		int prunedPlans = store.prunePlans(PLAN_RETENTION_DAYS);
		if (prunedPlans > 0)
		{
			System.out.println("Pruned " + prunedPlans + " plans older than "
				+ PLAN_RETENTION_DAYS + " days.");
		}
		// Superseded model weights. Blanked rather than deleted, so the retrain history the monitor
		// charts survives while the 690KB of weights behind each old version does not. The first pass
		// on an existing database frees a large fraction of the file, and freed pages are only handed
		// back by a compaction -- which is safe here, before anything else has the file open.
		int prunedModels = store.pruneModels(MODEL_VERSIONS_KEPT);
		if (prunedModels > 0)
		{
			System.out.println("Reclaiming " + prunedModels + " superseded model versions. "
				+ "This takes a moment on a large file.");
			store.compact();
			System.out.println("Done.");
		}

		// Hand back the write-ahead log, after the sweeps rather than before them.
		//
		// SQLite reuses the WAL rather than shrinking it, so a heavy burst leaves it large for ever
		// even though almost none of it is in use -- it reached 72MB here against a 30MB database.
		// Checkpointing first was the obvious placement and the wrong one: the retention sweeps run
		// immediately afterwards and refill it, which is exactly what happened on the first attempt.
		// This is still before anything else opens the file, which is what makes a truncate possible
		// at all.
		store.checkpoint();

		Gson gson = new Gson();
		CompanionService service = new CompanionService(gson, store, companion);
		// Buy-limit windows outlast this process, so recover them before advising anything.
		service.rehydrate();
		service.refreshMarket();
		ApiServer api = new ApiServer(token, gson, service);
		api.start();
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> { Thread t = new Thread(r, "flipping-friend-market"); t.setDaemon(true); return t; });
		scheduler.scheduleWithFixedDelay(guarded(service::refreshMarket), 60, 60, TimeUnit.SECONDS);
		// A second timer that only asks whether the first one is still alive. A scheduled task takes
		// its own error reporting down with it when it dies, so nothing inside the ingestion loop can
		// report that the ingestion loop has stopped.
		scheduler.scheduleWithFixedDelay(guarded(service::checkIngestion), 150, 60, TimeUnit.SECONDS);
		// On the clock, not on fills. A learning record with gaps where nothing traded cannot be read:
		// a flat line and a missing line look the same and mean opposite things.
		scheduler.scheduleWithFixedDelay(
			guarded(() -> service.recordLearningSnapshotIfDue(java.time.Instant.now().getEpochSecond())),
			60, 60, TimeUnit.SECONDS);
		// Retraining happens every twenty minutes or so while this process runs, so a startup-only
		// sweep would leave a long session growing by roughly 34MB a day regardless. No compaction
		// here: freed pages are reused by the next snapshot, which is all that is needed to hold the
		// file steady, and VACUUM would lock the database against the monitor reading it.
		scheduler.scheduleWithFixedDelay(guarded(() ->
		{
			try
			{
				store.pruneModels(MODEL_VERSIONS_KEPT);
			}
			catch (Exception ex)
			{
				System.err.println("could not prune model snapshots: " + ex);
			}
		}), 1, 1, TimeUnit.HOURS);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> { scheduler.shutdownNow(); api.close(); service.close(); try { store.close(); } catch (Exception ignored) { } }));
		System.out.println("Flipping Friend companion ready on loopback port " + ApiServer.PORT + ".");
		Thread.currentThread().join();
	}

	/**
	 * Stops a scheduled task from killing its own schedule.
	 * <p>
	 * ScheduledExecutorService cancels a repeating task permanently the first time it throws, and says
	 * nothing. Both tasks here already catch Throwable internally; this is the belt to that pair of
	 * braces, because the failure mode is silent and lasts until someone restarts the process.
	 */
	private static Runnable guarded(Runnable task)
	{
		return () ->
		{
			try
			{
				task.run();
			}
			catch (Throwable ex)
			{
				System.err.println("scheduled task threw, continuing: " + ex);
				ex.printStackTrace();
			}
		};
	}

	private static String token(Path propertiesFile) throws Exception
	{
		Properties properties = new Properties();
		if (Files.isRegularFile(propertiesFile))
		{
			try (java.io.Reader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) { properties.load(reader); }
			String existing = properties.getProperty("token");
			if (existing != null && !existing.isBlank()) return existing;
		}
		byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		properties.setProperty("token", token);
		try (java.io.Writer writer = Files.newBufferedWriter(propertiesFile, StandardCharsets.UTF_8)) { properties.store(writer, "Flipping Friend local companion credential"); }
		return token;
	}
}
