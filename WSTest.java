import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WSTest {
    public static void main(String[] args) throws Exception {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("wss://prices.runescape.wiki/api/ws").build();
        WebSocket ws = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("Opened");
                // The wiki API docs may say something different, let's just listen.
                // It might need "{\"type\":\"subscribe\"}" or something. Let's send a guess if it's silent.
                // webSocket.send("{\"type\":\"subscribe\"}");
            }
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                System.out.println("Msg: " + text);
                System.exit(0);
            }
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                t.printStackTrace();
                System.exit(1);
            }
        });
        Thread.sleep(10000);
    }
}
