package com.flippingfriend;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.model.SuggestionEngine;
import com.flippingfriend.overlay.GeWidgetResolver;
import com.flippingfriend.overlay.StepGuide;
import com.flippingfriend.session.OfferTracker;
import com.flippingfriend.ui.FlippingFriendPanel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.assertTrue;

/** Execute the production refresh path with mocked UI callbacks and no game or HTTP calls. */
public class UnsupportedOfferAdviceTest
{
	private static void inject(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	@Test public void unresolvedObservationWithdrawsAdviceBeforeAnyAccountPublication() throws Exception
	{
		FlippingFriendPlugin plugin = new FlippingFriendPlugin();
		OfferTracker tracker = Mockito.mock(OfferTracker.class);
		Mockito.when(tracker.hasCompleteOfferEvidence()).thenReturn(false);
		ClientThread clientThread = Mockito.mock(ClientThread.class);
		Mockito.doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
			.when(clientThread).invokeLater(Mockito.any(Runnable.class));
		StepGuide guide = Mockito.mock(StepGuide.class);
		FlippingFriendPanel panel = Mockito.mock(FlippingFriendPanel.class);
		CountDownLatch repaint = new CountDownLatch(1);
		Mockito.doAnswer(call -> { repaint.countDown(); return null; }).when(panel).refresh();
		CompanionClient companion = Mockito.mock(CompanionClient.class);
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try
		{
			inject(plugin, "offerTracker", tracker);
			inject(plugin, "clientThread", clientThread);
			inject(plugin, "configManager", Mockito.mock(net.runelite.client.config.ConfigManager.class));
			inject(plugin, "stepGuide", guide);
			inject(plugin, "widgetResolver", Mockito.mock(GeWidgetResolver.class));
			inject(plugin, "engine", Mockito.mock(SuggestionEngine.class));
			inject(plugin, "panel", panel);
			inject(plugin, "companion", companion);
			inject(plugin, "worker", worker);
			Method refresh = FlippingFriendPlugin.class.getDeclaredMethod("requestEngineRefresh");
			refresh.setAccessible(true);
			refresh.invoke(plugin);
			assertTrue("blocked refresh must replace the old card", repaint.await(3, TimeUnit.SECONDS));
			Mockito.verify(guide).withdrawAdvice(Mockito.argThat(value -> value != null && !value.isActionable()));
			Mockito.verifyNoInteractions(companion);
		}
		finally { worker.shutdownNow(); assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS)); }
	}
}
