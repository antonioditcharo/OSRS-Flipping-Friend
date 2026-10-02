package com.flippingfriend.companion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PriceOffsetIntegrationBoundaryTest
{
	@Test
	public void candidateFactoryUsesSharedOffsetArithmeticWithoutChangingItsTacticBoundary() throws Exception
	{
		Path source = root().resolve(
			"companion/src/main/java/com/flippingfriend/companion/CandidateFactory.java");
		String text = Files.readString(source, StandardCharsets.UTF_8);

		assertTrue(text.contains("PriceOffset.apply(screened.price.getLow(), buyOffset)"));
		assertTrue(text.contains("PriceOffset.apply(screened.price.getHigh(), sellOffset)"));
		assertTrue(text.contains("appetite.getBuyOffsets()"));
		assertTrue(text.contains("appetite.getSellOffsets()"));
		assertTrue(text.contains("quotableQuantity("));
		assertTrue(text.contains("SIZE_GRID"));
		assertTrue(text.contains("new PortfolioCandidate("));
		assertFalse(text.contains("private static int shift("));
	}

	private static Path root()
	{
		Path current = Path.of("").toAbsolutePath();
		while (current != null && !Files.exists(current.resolve("settings.gradle")))
			current = current.getParent();
		return current;
	}
}
