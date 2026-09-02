package com.flippingfriend.model.arbitrage;

import com.flippingfriend.model.SuggestionType;
import java.util.Collections;
import java.util.Map;

/**
 * Represents a relationship between items that can be transformed into one another.
 * This includes packing/unpacking sets, and decanting potions.
 */
public class ArbitrageRecipe
{
	private final Map<Integer, Integer> inputs;
	private final Map<Integer, Integer> outputs;
	private final SuggestionType actionType;
	private final String name;

	public ArbitrageRecipe(String name, Map<Integer, Integer> inputs, Map<Integer, Integer> outputs, SuggestionType actionType)
	{
		this.name = name;
		this.inputs = Collections.unmodifiableMap(inputs);
		this.outputs = Collections.unmodifiableMap(outputs);
		this.actionType = actionType;
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

	public SuggestionType getActionType()
	{
		return actionType;
	}
}
