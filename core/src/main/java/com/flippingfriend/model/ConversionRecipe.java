package com.flippingfriend.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One conversion the game offers: what goes in, what comes out, and where it is done.
 *
 * <p>Replaces {@code ArbitrageRecipe}, which carried inputs, outputs and a label and had no way to
 * say the one thing that decides whether a recipe is real: <b>whether it runs backwards</b>.
 *
 * <p>The old registry generated an unpack for every pack it registered. That is correct for a Grand
 * Exchange armour set, where the clerk will assemble and disassemble all day for free. It is false
 * for a godsword: attaching a hilt to a blade cannot be undone, and neither can smithing three
 * shards into the blade. Five of the eleven registered conversions produced a reverse recipe for an
 * operation the game does not offer — so a favourable quote on an Armadyl hilt would have advised
 * buying a godsword and taking it apart, which is not a thing that can be done at any price.
 *
 * <p>A recipe therefore declares its own direction, and {@link #reverse} exists only for the ones
 * that have one. Where it is done is carried too, because "free and instant at the clerk" and
 * "eighty Smithing at an anvil" are not the same offer even when the arithmetic is.
 */
public final class ConversionRecipe
{
	/** Where a conversion happens, which decides what it really costs to run. */
	public enum Venue
	{
		/** The Grand Exchange sets clerk. Free, instant, reversible, no requirements. */
		EXCHANGE_CLERK("at the Grand Exchange sets clerk"),
		/** Bob Barter, also at the Exchange. Free, instant, and conserves total doses. */
		DECANTER("at Bob Barter in the Grand Exchange"),
		/** An anvil, a furnace, a skill level. Real requirements and real time. */
		SKILL("with a skill, away from the Exchange");

		private final String where;

		Venue(String where)
		{
			this.where = where;
		}

		public String describe()
		{
			return where;
		}

		/** True when the conversion costs nothing but the walk to the clerk. */
		public boolean isFreeAndInstant()
		{
			return this != SKILL;
		}
	}

	private final String name;
	private final Map<Integer, Integer> inputs;
	private final Map<Integer, Integer> outputs;
	private final Venue venue;
	private final boolean reversible;

	public ConversionRecipe(String name, Map<Integer, Integer> inputs,
		Map<Integer, Integer> outputs, Venue venue, boolean reversible)
	{
		this.name = name;
		this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
		this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
		this.venue = venue;
		this.reversible = reversible;
	}

	public String getName()
	{
		return name;
	}

	public Map<Integer, Integer> getInputs()
	{
		return inputs;
	}

	public Map<Integer, Integer> getOutputs()
	{
		return outputs;
	}

	public Venue getVenue()
	{
		return venue;
	}

	/** Whether the game will run this backwards. Not every combination comes apart again. */
	public boolean isReversible()
	{
		return reversible;
	}

	/**
	 * The same conversion the other way round, or null when the game does not offer one.
	 * <p>
	 * Returning null rather than an impossible recipe is the whole point: a caller that iterates
	 * recipes cannot then advise an action that has no way of being carried out.
	 */
	public ConversionRecipe reverse(String reverseName)
	{
		if (!reversible)
		{
			return null;
		}
		return new ConversionRecipe(reverseName, outputs, inputs, venue, true);
	}
}
