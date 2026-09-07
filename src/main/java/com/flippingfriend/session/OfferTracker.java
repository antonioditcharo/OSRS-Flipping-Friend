package com.flippingfriend.session;

import com.flippingfriend.data.MarketSnapshot;
import com.flippingfriend.data.PluginStorage;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OfferTracker
{
	private static final Logger log = LoggerFactory.getLogger(OfferTracker.class);

	private static final Type OFFER_LIST = new TypeToken<List<TrackedOffer>>(){}.getType();
	private static final String FILE_NAME = "offers.json";
	private static final long SETTLED_RETENTION_SECONDS = 12 * 3600;

	private final PluginStorage storage;
	private final TransactionManager transactionManager;

	private final Map<Integer, TrackedOffer> bySlot = new ConcurrentHashMap<>();
	private final Map<Integer, TrackedOffer> settled = new ConcurrentHashMap<>();

	private Path loadedFrom;
	private volatile Runnable changeListener;
	private volatile AbandonedBuyListener abandonedBuyListener;
	private volatile MarketSnapshot market = MarketSnapshot.empty();

	@Inject
	public OfferTracker(PluginStorage storage, TransactionManager transactionManager)
	{
		this.storage = storage;
		this.transactionManager = transactionManager;
	}

	public void setChangeListener(Runnable listener) { this.changeListener = listener; }
	public void setMarket(MarketSnapshot market) { this.market = market; }

	public synchronized void load()
	{
		Path file = storage.accountDir().resolve(FILE_NAME);
		if (file.equals(loadedFrom)) return;

		bySlot.clear();
		settled.clear();
		long cutoff = Instant.now().getEpochSecond() - SETTLED_RETENTION_SECONDS;
		for (TrackedOffer offer : storage.<List<TrackedOffer>>readJson(file, OFFER_LIST, new ArrayList<>()))
		{
			if (offer.isCollected()) {
				if (offer.getLastChanged() >= cutoff) settled.put(offer.getSlot(), offer);
			} else {
				bySlot.put(offer.getSlot(), offer);
			}
		}
		loadedFrom = file;
	}

	public synchronized void save()
	{
		if (!storage.hasAccount()) return;
		Path file = storage.accountDir().resolve(FILE_NAME);
		List<TrackedOffer> all = new ArrayList<>(bySlot.values());
		all.addAll(settled.values());
		storage.writeJson(file, all, OFFER_LIST);
		loadedFrom = file;
	}

	public Collection<TrackedOffer> getOffers() { return bySlot.values(); }

	public Map<Integer, Integer> listedForSale()
	{
		Map<Integer, Integer> listed = new HashMap<>();
		for (TrackedOffer offer : bySlot.values())
		{
			if (!offer.isBuying() && offer.getRemaining() > 0)
			{
				listed.merge(offer.getItemId(), offer.getRemaining(), Integer::sum);
			}
		}
		return listed;
	}

	public Set<Integer> itemsWithOpenOffers()
	{
		Set<Integer> items = new HashSet<>();
		for (TrackedOffer offer : bySlot.values())
		{
			String state = offer.getState();
			if ("BUYING".equals(state) || "SELLING".equals(state)) items.add(offer.getItemId());
		}
		return items;
	}

	public Set<Integer> itemsBeingBought()
	{
		Set<Integer> items = new HashSet<>();
		for (TrackedOffer offer : bySlot.values())
		{
			if (offer.isBuying() && "BUYING".equals(offer.getState()) && offer.getRemaining() > 0) items.add(offer.getItemId());
		}
		return items;
	}

	public TrackedOffer getOffer(int slot) { return bySlot.get(slot); }

	public void onOfferChanged(int slot, GrandExchangeOffer offer)
	{
		if (offer == null) return;

		GrandExchangeOfferState state = offer.getState();
		Instant now = Instant.now();

		if (state == GrandExchangeOfferState.EMPTY)
		{
			TrackedOffer done = bySlot.remove(slot);
			if (done != null)
			{
				int outstanding = done.getQuantityFilled() - done.getRecordedQuantity();
				if (outstanding > 0)
				{
					com.flippingfriend.model.Transaction tx = applyFill(done, outstanding, null, now);
					transactionManager.record(tx);
					done.setRecordedQuantity(done.getQuantityFilled());
				}
				done.setCollected(true);
				settled.put(slot, done);
				pruneSettled(now);
				notifyChanged();
			}
			return;
		}

		boolean buying = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;

		int itemId = offer.getItemId();
		int price = offer.getPrice();
		int total = offer.getTotalQuantity();
		int filled = offer.getQuantitySold();

		TrackedOffer tracked = bySlot.get(slot);
		if (tracked == null || !tracked.matches(itemId, buying, price, total) || filled < tracked.getRecordedQuantity())
		{
			TrackedOffer previous = settled.get(slot);
			if (previous != null && previous.matches(itemId, buying, price, total) && filled >= previous.getRecordedQuantity())
			{
				settled.remove(slot);
				tracked = previous;
				tracked.setCollected(false);
				bySlot.put(slot, tracked);
			}
			else
			{
				settled.remove(slot);
				tracked = new TrackedOffer(slot, itemId, buying, price, total, now.getEpochSecond());
				tracked.setItemName(market.getItemName(itemId));
				bySlot.put(slot, tracked);
			}
		}

		int delta = filled - tracked.getRecordedQuantity();
		String previousState = tracked.getState();
		boolean progressMade = delta > 0 || !state.name().equals(previousState);

		tracked.setQuantityFilled(filled);
		tracked.setSpent(offer.getSpent());
		tracked.setState(state.name());
		
		if (progressMade) tracked.setLastChanged(now.getEpochSecond());

		if (state == GrandExchangeOfferState.CANCELLED_BUY && GrandExchangeOfferState.BUYING.name().equals(previousState))
		{
			AbandonedBuyListener listener = abandonedBuyListener;
			if (listener != null) listener.onBuyAbandoned(itemId, filled, total, tracked.minutesOpen(now.getEpochSecond()));
		}

		if (delta > 0)
		{
			com.flippingfriend.model.Transaction tx = applyFill(tracked, delta, offer, now);
			tracked.setRecordedQuantity(filled);
			transactionManager.record(tx);
		}

		notifyChanged();
	}

	private void pruneSettled(Instant now)
	{
		long cutoff = now.getEpochSecond() - SETTLED_RETENTION_SECONDS;
		settled.values().removeIf(offer -> offer.getLastChanged() < cutoff);
	}

	private com.flippingfriend.model.Transaction applyFill(TrackedOffer tracked, int quantity, GrandExchangeOffer offer, Instant now)
	{
		int itemId = tracked.getItemId();
		String itemName = market.getItemName(itemId);
		tracked.setItemName(itemName);

		long spentInThisFill = 0;
		if (tracked.isBuying())
		{
			long spentSoFar = offer == null ? tracked.getSpent() : offer.getSpent();
			long cost = Math.max(0, spentSoFar - tracked.getRecordedSpent());
			if (cost <= 0) cost = (long) tracked.getPrice() * quantity;
			tracked.setRecordedSpent(Math.max(spentSoFar, tracked.getRecordedSpent() + cost));
			spentInThisFill = cost;
		}
		else
		{
			long grossSoFar = offer == null ? tracked.getSpent() : offer.getSpent();
			long gross = Math.max(0, grossSoFar - tracked.getRecordedSpent());
			if (gross <= 0) gross = (long) tracked.getPrice() * quantity;
			tracked.setRecordedSpent(Math.max(grossSoFar, tracked.getRecordedSpent() + gross));
			spentInThisFill = gross;
		}

		int pricePerItem = (int) (spentInThisFill / Math.max(1, quantity));
		return new com.flippingfriend.model.Transaction(
			UUID.randomUUID(),
			itemId,
			quantity,
			pricePerItem,
			spentInThisFill,
			tracked.isBuying(),
			now.toEpochMilli(),
			tracked.getSlot(),
			tracked.getId()
		);
	}

	public void handleMissedTransaction(com.flippingfriend.model.Transaction tx)
	{
		log.info("Handling missed transaction from GE History: {}", tx);
		transactionManager.record(tx);
		notifyChanged();
	}

	public interface AbandonedBuyListener
	{
		void onBuyAbandoned(int itemId, int filled, int ordered, long minutesOpen);
	}

	public void setAbandonedBuyListener(AbandonedBuyListener listener) { this.abandonedBuyListener = listener; }

	private void notifyChanged()
	{
		Runnable listener = changeListener;
		if (listener != null)
		{
			try { listener.run(); } catch (Exception ex) { log.debug("offer change listener threw", ex); }
		}
	}

	public synchronized void clear()
	{
		bySlot.clear();
		loadedFrom = null;
	}
}
