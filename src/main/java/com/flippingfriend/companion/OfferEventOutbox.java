package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.PluginStorage;
import com.google.gson.Gson;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Account-scoped durable foundation for pending canonical offer events. */
@Singleton
public final class OfferEventOutbox
{
	static final String FILE_NAME = "offer-event-outbox.json";
	private static final int FORMAT_VERSION = 1;
	private final PluginStorage storage;
	private final Gson gson;

	@Inject
	public OfferEventOutbox(PluginStorage storage)
	{
		this.storage = storage;
		this.gson = storage.gson();
	}

	public synchronized OfferEvent enqueue(EventFactory factory) throws Exception
	{
		if (factory == null) throw new IllegalArgumentException("factory is required");
		State state = loadRequired();
		long sequence = state.nextSequence;
		String eventId = UUID.randomUUID().toString();
		OfferEvent event = factory.build(eventId, state.sessionId, sequence);
		validate(event, eventId, state.sessionId, sequence);
		State updated = state.copy();
		updated.nextSequence = sequence + 1;
		updated.pending.add(event);
		write(updated);
		return event;
	}

	public synchronized List<OfferEvent> pending() throws Exception
	{
		State state = loadRequired();
		List<OfferEvent> result = new ArrayList<>(state.pending);
		result.sort(Comparator.comparingLong(OfferEvent::getSequence));
		return Collections.unmodifiableList(result);
	}

        public synchronized boolean acknowledge(String eventId, String sessionId, long sequence) throws Exception
        {
                State state = loadRequired();
                for (int i = 0; i < state.pending.size(); i++)
                {
                        OfferEvent event = state.pending.get(i);
                        if (same(eventId, event.getEventId()) && same(sessionId, event.getSessionId()) && sequence == event.getSequence())
                        {
                                State updated = state.copy(); updated.pending.remove(i); write(updated); return true;
                        }
                }
                return false;
        }

	public synchronized String sessionId() throws Exception
	{
		return loadRequired().sessionId;
	}

	private State loadRequired() throws Exception
	{
		if (!storage.hasAccount()) throw new IllegalStateException("account profile is required");
		Path file = storage.accountDir().resolve(FILE_NAME);
		if (!Files.exists(file)) return new State(UUID.randomUUID().toString());
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			State state = gson.fromJson(reader, State.class);
			if (state == null || state.formatVersion != FORMAT_VERSION || blank(state.sessionId)
				|| state.nextSequence < 1 || state.pending == null)
			{
				throw new IllegalStateException("invalid offer event outbox");
			}
			return state;
		}
	}

	private void write(State state) throws Exception
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try
		{
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8))
			{
				gson.toJson(state, writer);
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (Exception failure)
		{
			Files.deleteIfExists(temp);
			throw failure;
		}
	}

	private static void validate(OfferEvent event, String eventId, String sessionId, long sequence)
	{
		if (event == null) throw new IllegalArgumentException("factory returned no event");
		if (!eventId.equals(event.getEventId()) || !sessionId.equals(event.getSessionId())
			|| sequence != event.getSequence())
		{
			throw new IllegalArgumentException("factory changed canonical event identity");
		}
	}

        private static boolean same(String left, String right) { return left != null && left.equals(right); }

	private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

	@FunctionalInterface
	public interface EventFactory
	{
		OfferEvent build(String eventId, String sessionId, long sequence);
	}

	private static final class State
	{
		private int formatVersion = FORMAT_VERSION;
		private String sessionId;
		private long nextSequence = 1;
		private List<OfferEvent> pending = new ArrayList<>();
		private State() { }
		private State(String sessionId) { this.sessionId = sessionId; }
		private State copy()
		{
			State copy = new State(sessionId);
			copy.nextSequence = nextSequence;
			copy.pending = new ArrayList<>(pending);
			return copy;
		}
	}
}
