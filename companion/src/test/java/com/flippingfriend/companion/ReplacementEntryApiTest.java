package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.net.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ReplacementEntryApiTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    HttpURLConnection connection(ApiServer api,String path,String account,String token)throws Exception {
        var c=(HttpURLConnection)new URL("http://127.0.0.1:"+api.port()+"/v1/"+path).openConnection();
        c.setConnectTimeout(3000);c.setReadTimeout(3000);
        if(account!=null)c.setRequestProperty(AccountIdentity.HEADER,account);
        if(token!=null)c.setRequestProperty("X-Flipping-Friend-Token",token);return c;
    }
    @Test public void theBoundedEndpointIsReadOnlyAuthenticatedAndAccountScoped()throws Exception {
        var root=folder.newFolder().toPath();var gson=new Gson();String account=AccountIdentity.fromProfileKey("one");
        try(var store=new SqliteStore(root.resolve("legacy.db"));var host=new CompanionService(gson,store);
            var accounts=new CompanionAccounts(root.resolve("accounts"),host);
            var api=new ApiServer("test-token",gson,host,accounts,new InetSocketAddress("127.0.0.1",0))) {
            api.start();String path="accounts/"+account+"/policy/replacement?item=561&quantityCap=6";
            var c=connection(api,path,account,null);try{assertEquals(401,c.getResponseCode());}finally{c.disconnect();}
            c=connection(api,path,null,"test-token");try{assertEquals(400,c.getResponseCode());}finally{c.disconnect();}
            c=connection(api,path,AccountIdentity.fromProfileKey("two"),"test-token");try{assertEquals(400,c.getResponseCode());}finally{c.disconnect();}
            c=connection(api,path,account,"test-token");try {
                assertEquals(200,c.getResponseCode());
                var reply=gson.fromJson(new String(c.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),ReplacementEntry.class);
                assertEquals(account,reply.getAccountId());assertEquals(6,reply.getQuantityCap());
                assertEquals(OfferLifecycleAction.WAIT,reply.getDecision().getAction());
            }finally{c.disconnect();}
            c=connection(api,path,account,"test-token");c.setRequestMethod("POST");try{assertEquals(405,c.getResponseCode());}finally{c.disconnect();}
            for(String query:new String[]{"item=0&quantityCap=6","item=561&quantityCap=-1","item=561&quantityCap=2147483648","item=561&quantityCap=6&cash=100000000"}) {
                c=connection(api,"accounts/"+account+"/policy/replacement?"+query,account,"test-token");
                try{assertEquals(query,400,c.getResponseCode());}finally{c.disconnect();}
            }
            assertEquals(0,store.positionEventCount());assertEquals(0,store.eventCount(RiskWindow.EVENT_TYPE));
        }
    }
}
