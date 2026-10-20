package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TradeReviewLifecycleTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void archivedDisplayCannotResurrectAcrossProfilesLogoutOrExpiredReceipts()throws Exception {
        var root=folder.newFolder().toPath();var one=TestStorage.rootedAt(root,"one");var two=TestStorage.rootedAt(root,"two");
        String first=one.accountId(),second=two.accountId();var firstDir=one.accountDir();var secondDir=two.accountDir();
        var storage=mock(PluginStorage.class);when(storage.accountId()).thenReturn(first);
        when(storage.accountDir(first)).thenReturn(firstDir);when(storage.accountDir(second)).thenReturn(secondDir);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger());client.resumeOfferReplay();
        var field=CompanionClient.class.getDeclaredField("tradeReview");field.setAccessible(true);
        var review=new TradeReviewSnapshot(first,Instant.now().getEpochSecond(),1,Map.of("source",TradeReviewSnapshot.ARCHIVED),Set.of("source"));
        field.set(client,review);assertSame(review,client.tradeReview());
        when(storage.accountId()).thenReturn(second);assertNull(client.tradeReview());
        when(storage.accountId()).thenReturn(first);assertNull(client.tradeReview());
        field.set(client,review);client.pauseOfferReplay();client.resumeOfferReplay();assertNull(client.tradeReview());
        field.set(client,new TradeReviewSnapshot(first,Instant.now().getEpochSecond()-151,1,review.states,review.archivedSources));
        assertNull(client.tradeReview());client.reviewTrades(first,1,List.of("source"),false);assertFalse(client.tradeReviewUpdating());
    }
}
