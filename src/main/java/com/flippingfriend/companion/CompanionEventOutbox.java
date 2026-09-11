package com.flippingfriend.companion;

import com.flippingfriend.data.PluginStorage;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Durable, account-scoped FIFO queue for companion event requests.
 *
 * <p>An entry is accepted only after the complete queue has been atomically written to disk.
 * Delivery is strictly ordered. A delivered entry is removed only after the shortened queue has
 * also been persisted. If that second write fails, the entry remains queued and may be delivered
 * again later. Companion event endpoints must therefore remain idempotent.</p>
 */
@Singleton
public class CompanionEventOutbox
{
	static final int MAX_ENTRIES = 1_000;
	private static final String FILE_NAME = "companion-event-outbox.json";
	private static final Type ENTRY_LIST =
		new TypeToken<List<Entry>>() { }.getType();

	private final PluginStorage storage;
	private Path loadedFile;
	private List<Entry> entries = new ArrayList<>();

	@Inject
	public CompanionEventOutbox(PluginStorage storage)
	{
		this.storage = storage;
	}

	/**
	 * Adds one request after its durable copy has reached the active account's directory.
	 *
	 * @return true when the request was durably accepted
	 */
	public synchronized boolean enqueue(String path, String body)
	{
		if (!storage.hasAccount() || path == null || path.isEmpty() || body == null)
		{
			return false;
		}

		loadCurrentAccount();

		if (entries.size() >= MAX_ENTRIES)
		{
			return false;
		}

		List<Entry> updated = new ArrayList<>(entries);
		updated.add(new Entry(path, body));

		if (!storage.writeJsonChecked(loadedFile, updated, ENTRY_LIST))
		{
			return false;
		}

		entries = updated;
		return true;
	}

	/**
	 * Delivers queued requests in FIFO order until the queue is empty or one delivery fails.
	 *
	 * <p>Stopping at the first failure preserves event order. A later call resumes with the same
	 * entry.</p>
	 *
	 * @return number of entries acknowledged and durably removed
	 */
	public synchronized int drain(Sender sender)
	{
		if (!storage.hasAccount() || sender == null)
		{
			return 0;
		}

		loadCurrentAccount();

		int delivered = 0;
		while (!entries.isEmpty())
		{
			Entry first = entries.get(0);

			try
			{
				if (!sender.send(first.getPath(), first.getBody()))
				{
					break;
				}
			}
			catch (Exception ex)
			{
				break;
			}

			List<Entry> remaining =
				new ArrayList<>(entries.subList(1, entries.size()));

			if (!storage.writeJsonChecked(loadedFile, remaining, ENTRY_LIST))
			{
				break;
			}

			entries = remaining;
			delivered++;
		}

		return delivered;
	}

	synchronized int size()
	{
		if (!storage.hasAccount())
		{
			return 0;
		}

		loadCurrentAccount();
		return entries.size();
	}

	private void loadCurrentAccount()
	{
		Path currentFile = storage.accountDir().resolve(FILE_NAME);

		if (currentFile.equals(loadedFile))
		{
			return;
		}

		List<Entry> loaded =
			storage.readJson(currentFile, ENTRY_LIST, Collections.emptyList());

		entries = loaded == null
			? new ArrayList<>()
			: new ArrayList<>(loaded);
		loadedFile = currentFile;
	}

	@FunctionalInterface
	public interface Sender
	{
		boolean send(String path, String body) throws Exception;
	}

	public static final class Entry
	{
		private String path;
		private String body;

		public Entry()
		{
		}

		Entry(String path, String body)
		{
			this.path = path;
			this.body = body;
		}

		public String getPath()
		{
			return path;
		}

		public String getBody()
		{
			return body;
		}
	}
}
