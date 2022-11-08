package com.example;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.json.JSONObject;
import org.webrtc.VideoCapturer;

import java.util.Hashtable;

import dji.sdk.sdkmanager.DJISDKManager;
import static io.socket.client.Socket.EVENT_DISCONNECT;

import com.example.SocketConnection;

/**
 * The DJIStreamer class will manage all ongoing P2P connections
 * with clients, who desire videofeed.
 */
public class DJIStreamer {
    private static final String TAG = "DJIStreamer";

    private String droneDisplayName = "";
    private final Context context;
    private final Hashtable<String, WebRTCClient> ongoingConnections = new Hashtable<>();
    private final Socket socket;

    public DJIStreamer(Context context){
        this.droneDisplayName = DJISDKManager.getInstance().getProduct().getModel().getDisplayName();
        this.context = context;

        setupSocketEvent();
    }

    private WebRTCClient getClient(String socketID){
        return ongoingConnections.getOrDefault(socketID, null);
    }

    private void removeClient(String socketID){
        // TODO: Any other cleanup necessary?.. Let the client stop the VideoCapturer though.
        ongoingConnections.remove(socketID);
    }

    private WebRTCClient addNewClient(String socketID){
        VideoCapturer videoCapturer = new DJIVideoCapturer(droneDisplayName);
        WebRTCClient client = new WebRTCClient(socketID, context, videoCapturer, new WebRTCMediaOptions());
        client.setConnectionChangedListener(new WebRTCClient.PeerConnectionChangedListener() {
            @Override
            public void onDisconnected() {
                removeClient(client.peerSocketID);
                Log.d(TAG, "DJIStreamer has removed connection from table. Remaining active sessions: " + ongoingConnections.size());
            }
        });
        ongoingConnections.put(socketID, client);
        return client;
    }

    private void setupSocketEvent(){
        SocketConnection.getInstance().on("webrtc_msg", args -> {

            Handler mainHandler = new Handler(context.getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    String peerSocketID = (String)args[0]; // The web-client sending a message
                    Log.d(TAG, "Received WebRTCMessage: " + peerSocketID);

                    WebRTCClient client = getClient(peerSocketID);

                    if (client == null){
                        // A new client wants to establish a P2P
                        client = addNewClient(peerSocketID);
                    }

                    // Then just pass the message to the client
                    JSONObject message = (JSONObject) args[1];
                    client.handleWebRTCMessage(message);
                }
            };
            mainHandler.post(myRunnable);
        }).on(EVENT_DISCONNECT, args -> {
            Log.d(TAG, "connectToSignallingServer: disconnect");
        });
    }
}
