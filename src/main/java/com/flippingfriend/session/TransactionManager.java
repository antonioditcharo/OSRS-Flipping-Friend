package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.model.Transaction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TransactionManager
{
	private static final Logger log = LoggerFactory.getLogger(TransactionManager.class);
	private static final String FILE_NAME = "transactions.jsonl";
	private static final int MAX_LOADED = 10000;

	private final PluginStorage storage;
	private final List<Transaction> history = Collections.synchronizedList(new ArrayList<>());
	private final List<Consumer<Transaction>> listeners = new ArrayList<>();
	private Path loadedFrom;

	@Inject
	public TransactionManager(PluginStorage storage)
	{
		this.storage = storage;
	}

	public synchronized void addListener(Consumer<Transaction> listener)
	{
		listeners.add(listener);
	}

	public synchronized void load()
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		if (file.equals(loadedFrom))
		{
			return;
		}

		history.clear();

		List<String> lines = storage.readLines(file);
		int from = Math.max(0, lines.size() - MAX_LOADED);
		for (int i = 0; i < lines.size(); i++)
		{
			String line = lines.get(i).trim();
			if (line.isEmpty())
			{
				continue;
			}
			try
			{
				Transaction record = storage.gson().fromJson(line, Transaction.class);
				if (record != null)
				{
					if (i >= from)
					{
						history.add(record);
					}
				}
			}
			catch (Exception ex)
			{
				log.debug("skipping unreadable transaction line {}", i);
			}
		}
		loadedFrom = file;
	}

	public void record(Transaction transaction)
	{
		history.add(transaction);
		storage.appendLine(storage.accountDir().resolve(FILE_NAME), storage.gson().toJson(transaction));
		
		synchronized (this)
		{
			for (Consumer<Transaction> listener : listeners)
			{
				try
				{
					listener.accept(transaction);
				}
				catch (Exception ex)
				{
					log.error("Error in TransactionManager listener", ex);
				}
			}
		}
	}

	public List<Transaction> getHistory()
	{
		synchronized (history)
		{
			return new ArrayList<>(history);
		}
	}

	public List<Transaction> getTransactionsForItem(int itemId)
	{
		synchronized (history)
		{
			return history.stream()
				.filter(t -> t.getItemId() == itemId)
				.collect(Collectors.toList());
		}
	}

	public synchronized void clear()
	{
		history.clear();
		loadedFrom = null;
	}
}
