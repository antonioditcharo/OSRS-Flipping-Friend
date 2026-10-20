package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.PluginStorage;
import com.google.gson.Gson;
import java.io.IOException;
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
    private final ReplaceCheckpoint checkpoint;
    /** Valid unsaved appends retain their exact IDs/order/bindings until this account can commit. */
    private final java.util.Map<String,State> unsaved=new java.util.HashMap<>();

	@Inject
	public OfferEventOutbox(PluginStorage storage)
	{ this(storage,(file,temp)->{}); }

    OfferEventOutbox(PluginStorage storage,ReplaceCheckpoint checkpoint)
	{
		this.storage = storage;
		this.gson = storage.gson();
        this.checkpoint=checkpoint;
	}
    @FunctionalInterface interface ReplaceCheckpoint {void beforeReplace(Path file,Path temp)throws Exception;}

	public synchronized OfferEvent enqueue(EventFactory factory) throws Exception
	{
        return enqueue(factory,false);
    }
    /** Offer attribution and its delivery event share one account-file replacement. */
    public synchronized OfferEvent enqueueObservedOffer(EventFactory factory) throws Exception
	{
        return enqueue(factory,true);
    }
    private OfferEvent enqueue(EventFactory factory,boolean bindPrediction) throws Exception
	{
		if (factory == null) throw new IllegalArgumentException("factory is required");
        return enqueue(java.util.Collections.singletonList(factory),bindPrediction).get(0);
    }
    /** One copied frame, one local replacement; every source retains its own identity and order. */
    public synchronized List<OfferEvent> enqueueObservedOffers(List<EventFactory> factories)throws Exception {
        return enqueue(factories,true);
    }
    private List<OfferEvent> enqueue(List<EventFactory> factories,boolean bindPrediction)throws Exception {
        if(factories==null || factories.isEmpty() || factories.size()>8 || factories.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("One to eight observation factories are required");
		State state = loadRequired(true);
		State updated = state.copy();
        List<OfferEvent> created=new ArrayList<>();
        java.util.Set<Integer> observedSlots=new java.util.HashSet<>();
        for(var factory:factories) {
            long sequence=updated.nextSequence;String eventId=UUID.randomUUID().toString();
            OfferEvent event=factory.build(eventId,state.sessionId,sequence);
            validate(event,eventId,state.sessionId,sequence);
            if(event.getAccountId()!=null && !state.accountId.equals(event.getAccountId()))
                throw new IllegalArgumentException("factory changed account identity");
            event=event.withAccountId(state.accountId);updated.nextSequence=Math.addExact(sequence,1);
            if(bindPrediction && !observedSlots.add(event.getSlot()))throw new IllegalArgumentException("Duplicate observation slot");
            if(bindPrediction)event=bindPrediction(event,updated);
            updated.pending.add(event);created.add(event);
        }
		write(updated,true);
		return Collections.unmodifiableList(created);
	}
    private OfferEvent bindPrediction(OfferEvent event,State updated) {
        if(event.getSlot()<0 || event.getSlot()>=8)throw new IllegalArgumentException("invalid observation slot");
        if("EMPTY".equals(event.getEventType())) {
            if(event.isAuthenticatedObservation())updated.predictions.remove(event.getSlot());
            return event.withBoundPrediction(null,false);
        }
        PredictionBinding previous=updated.predictions.get(event.getSlot());
        if(previous==null || !sameOffer(previous.origin,event)) {
            boolean timely=event.isAuthenticatedObservation() && event.getFilledQuantity()==0
                && ("BUYING".equals(event.getEventType()) || "SELLING".equals(event.getEventType()))
                && event.getFirstSeenAt()>0 && event.getFirstSeenAt()==event.getObservedAt()
                && event.getAdviceObservedAt()>0 && event.getAdviceObservedAt()<=event.getFirstSeenAt()
                && event.getFirstSeenAt()-event.getAdviceObservedAt()<=SuggestionLedger.ATTRIBUTION_WINDOW_SECONDS;
            // Equal second timestamps preserve diagnostic association, not ordering proof.
            OfferEvent bound=event.withBoundPrediction(timely?event:null,timely && event.getAdviceObservedAt()<event.getFirstSeenAt());
            updated.predictions.put(event.getSlot(),new PredictionBinding(bound,!bound.isPredictionComparable()));
            return bound;
        }
        boolean changed=previous.origin.getPrice()!=event.getPrice() || previous.origin.getTotalQuantity()!=event.getTotalQuantity();
        PredictionBinding binding=new PredictionBinding(previous.origin,previous.invalidated||changed);
        updated.predictions.put(event.getSlot(),binding);
        return event.withBoundPrediction(binding.origin,!binding.invalidated);
    }
    private static boolean sameOffer(OfferEvent a,OfferEvent b) {
        return same(a.getAccountId(),b.getAccountId()) && same(a.getSessionId(),b.getSessionId())
            && same(a.getOfferIdentity(),b.getOfferIdentity()) && a.getSlot()==b.getSlot()
            && a.getItemId()==b.getItemId() && a.isBuying()==b.isBuying() && a.getFirstSeenAt()==b.getFirstSeenAt();
    }
    private static final class PredictionBinding {
        private final OfferEvent origin;
        private final boolean invalidated;
        PredictionBinding(OfferEvent origin,boolean invalidated){this.origin=origin;this.invalidated=invalidated;}
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

    /** Remove only a complete matched prefix; concurrent appends remain durable and ordered. */
    synchronized boolean acknowledgePrefix(List<OfferEvent> prefix,String expectedAccount)throws Exception {
        if(prefix==null || expectedAccount==null || !expectedAccount.equals(storage.accountId()))return false;
        if(prefix.isEmpty())return true;
        State state=loadRequired();List<OfferEvent> ordered=new ArrayList<>(state.pending);
        ordered.sort(Comparator.comparingLong(OfferEvent::getSequence));
        if(prefix.size()>ordered.size())return false;
        java.util.Set<String> accepted=new java.util.HashSet<>();
        for(int i=0;i<prefix.size();i++) {
            var ack=prefix.get(i);var pending=ordered.get(i);
            if(ack==null || !expectedAccount.equals(ack.getAccountId()) || !same(ack.getEventId(),pending.getEventId())
                || !same(ack.getSessionId(),pending.getSessionId()) || ack.getSequence()!=pending.getSequence())return false;
            accepted.add(ack.getEventId());
        }
        State updated=state.copy();updated.pending.removeIf(e->accepted.contains(e.getEventId()));write(updated);return true;
    }
	public synchronized String sessionId() throws Exception
	{
		return loadRequired().sessionId;
	}

	private State loadRequired() throws Exception { return loadRequired(false); }
	private State loadRequired(boolean appending) throws Exception
	{
		if (!storage.hasAccount()) throw new IllegalStateException("account profile is required");
		String accountId = com.flippingfriend.core.AccountIdentity.require(storage.accountId());
		State retained=unsaved.get(accountId);
		if(retained!=null) {
            // Another callback must join the retained tail even if the disk is still unavailable.
            // Reads/ACKs cannot expose a prefix or deliver sources before that entire tail commits.
            if(!appending)throw new IOException("offer observations await durable local storage");
            return retained;
        }
		Path file = storage.accountDir(accountId).resolve(FILE_NAME);
		byte[] image=readImage(file);
		if (image==null) return new State(UUID.randomUUID().toString(), accountId, file);
		State state=gson.fromJson(new String(image,StandardCharsets.UTF_8),State.class);
		if (state == null || state.formatVersion != FORMAT_VERSION || blank(state.sessionId)
			|| state.nextSequence < 1 || state.pending == null)
		{
			throw new IllegalStateException("invalid offer event outbox");
		}
		boolean upgraded = state.accountId == null;
		if (!upgraded && !accountId.equals(state.accountId))
			throw new IllegalStateException("outbox belongs to another account");
		state.accountId = accountId;
		state.file = file;
        state.committedImage=image;
		java.util.Set<String> eventIds = new java.util.HashSet<>();
		java.util.Set<Long> sequences = new java.util.HashSet<>();
		for (int index = 0; index < state.pending.size(); index++)
		{
			OfferEvent event = state.pending.get(index);
			if (event == null) throw new IllegalStateException("invalid pending event");
			if (!"1".equals(event.getSchemaVersion()) || blank(event.getEventId())
				|| !state.sessionId.equals(event.getSessionId())
				|| event.getSequence() < 1 || event.getSequence() >= state.nextSequence
				|| !eventIds.add(event.getEventId()) || !sequences.add(event.getSequence()))
				throw new IllegalStateException("invalid pending event identity or sequence");
			if (event.getAccountId() == null)
			{
				state.pending.set(index, event.withAccountId(accountId));
				upgraded = true;
			}
			else if (!accountId.equals(event.getAccountId()))
				throw new IllegalStateException("pending event belongs to another account");
		}
        if(state.predictions==null) {
            state.predictions=new java.util.HashMap<>();
            // Interpret only already validated/scoped pending identities; never rewrite their claims.
            List<OfferEvent> ordered=new ArrayList<>(state.pending);ordered.sort(Comparator.comparingLong(OfferEvent::getSequence));
            for(OfferEvent pending:ordered) if(pending.getSlot()>=0 && pending.getSlot()<8) {
                if("EMPTY".equals(pending.getEventType())) {
                    if(pending.isAuthenticatedObservation())state.predictions.remove(pending.getSlot());
                }else if(!blank(pending.getOfferIdentity()))state.predictions.put(pending.getSlot(),new PredictionBinding(pending,true));
            }
        }
        for(var entry:state.predictions.entrySet()) {
            PredictionBinding binding=entry.getValue();OfferEvent origin=binding==null?null:binding.origin;
            if(entry.getKey()==null || entry.getKey()<0 || entry.getKey()>=8 || origin==null
                || origin.getSlot()!=entry.getKey() || !accountId.equals(origin.getAccountId())
                || !state.sessionId.equals(origin.getSessionId()) || origin.getSequence()<1
                || origin.getSequence()>=state.nextSequence || blank(origin.getEventId()) || blank(origin.getOfferIdentity()))
                throw new IllegalStateException("invalid prediction binding scope");
            if(!"1".equals(origin.getSchemaVersion())
                || (origin.getPredictionEvidenceVersion()!=null && !"1".equals(origin.getPredictionEvidenceVersion()))
                || (!binding.invalidated && (!origin.isPredictionComparable()
                    || !origin.getEventId().equals(origin.getPredictionSourceEventId()))))
                throw new IllegalStateException("invalid prediction binding schema or source");
        }
		if (upgraded && !appending) write(state);
		return state;
	}
    /** Worker-only retry. Ordinary readers must withhold authority without doing a forced write. */
    synchronized void retryUnsaved()throws Exception {
        if(!storage.hasAccount())throw new IllegalStateException("account profile is required");
        String account=com.flippingfriend.core.AccountIdentity.require(storage.accountId());
        State retained=unsaved.get(account);if(retained!=null)write(retained,true);
    }

	private void write(State state) throws Exception
	{ write(state,false); }
	private void write(State state,boolean retainAppend) throws Exception
	{
		if(retainAppend)unsaved.put(state.accountId,state);
		if (!state.accountId.equals(storage.accountId()))
			throw new IllegalStateException("account changed before saving the outbox");
		Path file = state.file;
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try
		{
            verifyDestination(state);
            Files.createDirectories(file.getParent());
            byte[] image=gson.toJson(state).getBytes(StandardCharsets.UTF_8);
            try(var channel=java.nio.channels.FileChannel.open(temp,java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,java.nio.file.StandardOpenOption.WRITE)) {
                var buffer=java.nio.ByteBuffer.wrap(image);while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            checkpoint.beforeReplace(file,temp);
            if(!state.accountId.equals(storage.accountId()))throw new IllegalStateException("account changed before outbox replacement");
            verifyDestination(state);
            // Never fall back to a non-atomic replacement or expose an unforced delivery event.
			Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            state.committedImage=image;
            unsaved.remove(state.accountId,state);
		}
		catch (Exception failure)
		{
			try{Files.deleteIfExists(temp);}catch(Exception cleanup){failure.addSuppressed(cleanup);}
			throw failure;
		}
	}
    private void verifyDestination(State state)throws IOException {
        byte[] current=readImage(state.file);
        if(!java.util.Arrays.equals(current,state.committedImage))
            throw new IOException("outbox changed outside this observation commit; retained evidence awaits verification");
    }
    private static byte[] readImage(Path file)throws IOException {
        try{return Files.readAllBytes(file);}
        catch(java.nio.file.NoSuchFileException absent){return null;}
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
		private String accountId;
		private transient Path file;
        private transient byte[] committedImage;
		private long nextSequence = 1;
		private List<OfferEvent> pending = new ArrayList<>();
        private java.util.Map<Integer,PredictionBinding> predictions;
		private State() { }
		private State(String sessionId, String accountId, Path file)
		{ this.sessionId = sessionId; this.accountId = accountId; this.file = file; this.predictions=new java.util.HashMap<>(); }
		private State copy()
		{
			State copy = new State(sessionId, accountId, file);
			copy.committedImage=committedImage;
			copy.nextSequence = nextSequence;
			copy.pending = new ArrayList<>(pending);
            copy.predictions=new java.util.HashMap<>(predictions);
			return copy;
		}
	}
}
