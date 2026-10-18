package com.flippingfriend;

import com.google.gson.*;
import java.io.IOException;
import okhttp3.*;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DiscordWebhookClientTest {
    @Test public void configuredWebhookDispatchesOneJsonPostAndSuppressesMentions() throws Exception {
        OkHttpClient http=mock(OkHttpClient.class);Call call=mock(Call.class);
        when(http.newCall(any())).thenReturn(call);
        new DiscordWebhookClient(http,new Gson()).sendMessage("https://discord.com/api/webhooks/test/token","@everyone drop");
        var request=org.mockito.ArgumentCaptor.forClass(Request.class);verify(http).newCall(request.capture());
        assertEquals("POST",request.getValue().method());
        Buffer body=new Buffer();request.getValue().body().writeTo(body);
        JsonObject payload=new Gson().fromJson(body.readUtf8(),JsonObject.class);
        assertEquals("@everyone drop",payload.get("content").getAsString());
        assertEquals(0,payload.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
        verify(call).enqueue(any(Callback.class));
    }
    @Test public void blankMalformedAndForeignAddressesCannotThrowOrDispatch() {
        OkHttpClient http=mock(OkHttpClient.class);var client=new DiscordWebhookClient(http,new Gson());
        for(String url:new String[]{null,"","garbage","https://example.com/api/webhooks/x/y","http://discord.com/api/webhooks/x/y"})client.sendMessage(url,"test");
        verifyNoInteractions(http);
    }
}
