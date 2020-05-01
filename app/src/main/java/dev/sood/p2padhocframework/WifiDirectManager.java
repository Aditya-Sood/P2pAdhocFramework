package dev.sood.p2padhocframework;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import static android.os.Looper.getMainLooper;

/**
 * Manager for the Wifi-P2p API, used in the local file transfer module
 */
public class WifiDirectManager
        implements WifiP2pManager.ChannelListener, WifiP2pManager.PeerListListener,
        WifiP2pManager.ConnectionInfoListener,
        P2pAdhocBroadcastReceiver.P2pEventListener {

    private static final String TAG = "WifiDirectManager";
    private static final String SERVICE_INSTANCE_NAME = "p2padhocframework";
    public static final int INITIAL_COUNT = 0;

    private String androidId;

    private @NonNull Activity activity;
    private @NonNull Callbacks callbacks;

    /* Variables related to the WiFi P2P API */
    private boolean isWifiP2pEnabled = false; // Whether WiFi has been enabled or not
    private boolean shouldRetry = true;       // Whether channel has retried connecting previously

    private WifiP2pManager manager;         // Overall manager of Wifi p2p connections for the module
    private WifiP2pManager.Channel channel; // Interface to the device's underlying wifi-p2p framework

    private BroadcastReceiver receiver = null; // For receiving the broadcasts given by above filter

    private WifiP2pInfo groupInfo; // Corresponds to P2P group formed between the two devices

    private Map<String, PeerTransmissionState> mapPeerTransmissionState = new HashMap<>();
    private Map<String, Queue<DataPacket>> mapBroadcastPktQueue = new HashMap<>();

    private final Object mutexLockMapBroadcastPktQueue = new Object();

    public WifiDirectManager(@NonNull Activity activity, String androidId) {
        this.activity = activity;
        this.callbacks = (Callbacks) activity;
        this.androidId = androidId;

        mapBroadcastPktQueue.put(androidId, new LinkedList<DataPacket>());
    }

    /* Initialisations for using the WiFi P2P API */
    public void startWifiDirectManager() {
        manager = (WifiP2pManager) activity.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(activity, getMainLooper(), null);
        registerWifiDirectBroadcastReceiver();

        setDnsSdServiceResponseListener();
        removeAllCurrentBroadcasts();

        addMessageToBroadcastQueue(INITIAL_COUNT-1, "", androidId+" online");
        broadcastPackets();
        discoverPeerPackets();
    }

    public void registerWifiDirectBroadcastReceiver() {
        receiver = new P2pAdhocBroadcastReceiver(this);

        // For specifying broadcasts (of the P2P API) that the module needs to respond to
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        activity.registerReceiver(receiver, intentFilter);
    }

    public void unregisterWifiDirectBroadcastReceiver() {
        activity.unregisterReceiver(receiver);
    }

    public void addMessageToBroadcastQueue(final int sendCount, final String destination, final String message) {
        synchronized (mutexLockMapBroadcastPktQueue) {
            mapBroadcastPktQueue.get(androidId)
                    .add(new DataPacket(androidId, System.nanoTime()+"", sendCount+"", destination, message));
        }
    }

    public void broadcastPackets() {
        removeAllCurrentBroadcasts();
    }

    public void removeAllCurrentBroadcasts(){
        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(WifiDirectManager.TAG, "Removed all local services");
                addAllCurrentBroadcasts();
            }

            @Override
            public void onFailure(int reason) {
                Log.d(WifiDirectManager.TAG, "Failed to remove all local services");
            }
        });
    }

    public void addAllCurrentBroadcasts() {
        synchronized (mutexLockMapBroadcastPktQueue) {
            for(Map.Entry<String, Queue<DataPacket>> mapEntry : mapBroadcastPktQueue.entrySet()) {
                if(mapEntry.getValue().size() > 0) {
                    DataPacket dataPacket = mapEntry.getValue().remove();
                    Log.d(WifiDirectManager.TAG, "Prepping: " + dataPacket.toString());
                    WifiP2pDnsSdServiceInfo serviceInfo = getServiceInfo(dataPacket);
                    WifiDirectManager.this.addLocalService(serviceInfo);

                    if(mapEntry.getKey().equals(androidId)) {
                        String displayMsg;
                        if(dataPacket.getDestination().isEmpty()) {
                            displayMsg = "Broadcast: ";
                        } else {
                            displayMsg = "To "+ dataPacket.getDestination()+": ";
                        }
                        displayMsg += dataPacket.getMessage();
                        callbacks.addSentMessage(displayMsg);
                    }
                }
            }
        }
    }

    public WifiP2pDnsSdServiceInfo getServiceInfo(DataPacket dataPacket) {
        Map<String, String> serviceInfoMap = new HashMap<String, String>();
        serviceInfoMap.put("txtvers", "0.1"); // For version compatibility
        serviceInfoMap.put(DataPacket.KEY_SENDER_ID, dataPacket.getSenderId());
        serviceInfoMap.put(DataPacket.KEY_TIMESTAMP, dataPacket.getTimestamp()+"");
        serviceInfoMap.put(DataPacket.KEY_PKT_COUNT, dataPacket.getPktCount()+"");
        if(!dataPacket.getDestination().isEmpty()) {
            serviceInfoMap.put(DataPacket.KEY_DESTINATION, dataPacket.getDestination());
        }
        serviceInfoMap.put(DataPacket.KEY_MESSAGE, dataPacket.getMessage());

        WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE_NAME, "_bonjour._tcp", serviceInfoMap);

        return serviceInfo;
    }

    public void addLocalService(final WifiP2pServiceInfo servInfo) {
        manager.addLocalService(channel, servInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(WifiDirectManager.TAG, "Added service " + servInfo.toString());
                Toast.makeText(activity, "Added local service - " + servInfo.toString(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Log.d(WifiDirectManager.TAG, "Failed to add service " + servInfo.toString());
                Toast.makeText(activity, "Failed to add service", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void removeLocalService(final WifiP2pServiceInfo servInfo) {
        manager.removeLocalService(channel, servInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(WifiDirectManager.TAG, "Removed service " + servInfo.toString());
            }

            @Override
            public void onFailure(int reason) {
                Log.d(WifiDirectManager.TAG, "Failed to remove service " + servInfo.toString());
            }
        });
    }

    public void setDnsSdServiceResponseListener() {
        manager.setDnsSdResponseListeners(channel, new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice srcDevice) {
                Log.d(WifiDirectManager.TAG, "Dns Service Discovered: " + instanceName);
                Toast.makeText(activity, "Dns Service Discovered: " + instanceName, Toast.LENGTH_SHORT).show();
            }
        }, new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {

                if(txtRecordMap.get("txtvers") == null) {
                    return;
                }

                Log.d(WifiDirectManager.TAG, fullDomainName+" : "+txtRecordMap.get(DataPacket.KEY_SENDER_ID)+" - "+txtRecordMap.get(DataPacket.KEY_MESSAGE));

                String peerDeviceId = txtRecordMap.get(DataPacket.KEY_SENDER_ID);
                long peerTimestamp = Long.parseLong(txtRecordMap.get(DataPacket.KEY_TIMESTAMP));
                int peerPktCount = Integer.parseInt(txtRecordMap.get(DataPacket.KEY_PKT_COUNT));
                String destination = txtRecordMap.get(DataPacket.KEY_DESTINATION);
                String peerMessage = txtRecordMap.get(DataPacket.KEY_MESSAGE);

                if(peerDeviceId.equals(androidId)) {
                    Log.d(WifiDirectManager.TAG, "Cyclic broadcast, ignore message");
                    return;
                }

                synchronized (mutexLockMapBroadcastPktQueue) {
                    if(mapPeerTransmissionState.containsKey(peerDeviceId)) {
                        if((peerPktCount == 0 && peerTimestamp <= mapPeerTransmissionState.get(peerDeviceId).getLastTimestamp()) || (peerPktCount > 0 && peerPktCount <= mapPeerTransmissionState.get(peerDeviceId).getPktCount())) {
                            Log.d(WifiDirectManager.TAG, "Stale message ignored");
                            return;
                        } else {
                            mapPeerTransmissionState.get(peerDeviceId).setLastTimestamp(peerTimestamp);
                            mapPeerTransmissionState.get(peerDeviceId).setPktCount(peerPktCount);
                        }
                    } else {
                        mapPeerTransmissionState.put(peerDeviceId, new PeerTransmissionState(peerTimestamp, peerPktCount));
                        mapBroadcastPktQueue.put(peerDeviceId, new LinkedList<DataPacket>());
                    }

                    mapBroadcastPktQueue.get(peerDeviceId)
                            .add(new DataPacket(peerDeviceId, peerTimestamp+"",
                                    peerPktCount+"", destination, peerMessage));
                }

                if(destination == null || destination.equals(androidId)) {
                    callbacks.addReceivedMessage(peerDeviceId+": "+peerMessage+"\nTimestamp: "+peerTimestamp);
                    Log.d(WifiDirectManager.TAG, "Added message: "+peerDeviceId+" - "+peerMessage);
                } else {
                    Log.d(WifiDirectManager.TAG, "Message from" +peerDeviceId+" ignored");
                }

                Toast.makeText(activity, "Received message from Android ID:"+peerDeviceId+"\nMessage"
                        +peerPktCount+" :"+peerMessage+"\nTimestamp:"+peerTimestamp, Toast.LENGTH_LONG).show();
            }
        });

        addServiceRequest(WifiP2pDnsSdServiceRequest.newInstance(SERVICE_INSTANCE_NAME, "_bonjour._tcp"));
    }

    /**
     * Discovers all DNS service broadcasts - and hence the peer messages*/
    public void discoverPeerPackets() {
        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(WifiDirectManager.TAG, "Discovery initiated");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(WifiDirectManager.TAG, "Failed to initiate service discover service - " + reason);
                if(reason == 3) {
                    Toast.makeText(activity, "Unresponsive OS error, restart the app", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void addServiceRequest(final WifiP2pServiceRequest request) {
        manager.addServiceRequest(channel, request, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(WifiDirectManager.TAG, "addServiceRequest.onSuccess() for requests of type: " + request.getClass().getSimpleName());
            }

            @Override
            public void onFailure(int code) {
                Log.d(WifiDirectManager.TAG, "addServiceRequest.onFailure: " + code + ", for requests of type: "
                        + request.getClass().getSimpleName());
            }
        });
    }


    /* From P2pEventListener callback-interface*/
    @Override
    public void onWifiP2pStateChanged(boolean isEnabled) {
        this.isWifiP2pEnabled = isEnabled;

        if (!isWifiP2pEnabled) {
            callbacks.onConnectionToPeersLost();
        }

        Log.d(TAG, "WiFi P2P state changed - " + isWifiP2pEnabled);
    }

    @Override
    public void onPeersChanged() {
        /* List of available peers has changed, so request & use the new list through
         * PeerListListener.requestPeers() callback */
        manager.requestPeers(channel, this);
        Log.d(TAG, "P2P peers changed");
    }

    @Override
    public void onConnectionChanged(boolean isConnected) {
        Log.d(WifiDirectManager.TAG, "P2p Connnection Changed: isConnected " + isConnected);
    }

    @Override
    public void onDeviceChanged(@Nullable WifiP2pDevice userDevice) {
        callbacks.onUserDeviceDetailsAvailable(userDevice);
    }

    /* From WifiP2pManager.ChannelListener interface */
    @Override
    public void onChannelDisconnected() {
        // Upon disconnection, retry one more time
        if (shouldRetry) {
            Log.d(TAG, "Channel lost, trying again");
            callbacks.onConnectionToPeersLost();
            shouldRetry = false;
            manager.initialize(activity, getMainLooper(), this);
        }
    }

    /* From WifiP2pManager.PeerListListener callback-interface */
    @Override
    public void onPeersAvailable(@NonNull WifiP2pDeviceList peers) {
        callbacks.updateListOfAvailablePeers(peers);
    }

    /* From WifiP2pManager.ConnectionInfoListener callback-interface */
    @Override
    public void onConnectionInfoAvailable(@NonNull WifiP2pInfo groupInfo) {
        /* Devices have successfully connected, and 'info' holds information about the wifi p2p group formed */
        this.groupInfo = groupInfo;
        performHandshakeWithSelectedPeerDevice();
    }

    /* Helper methods */
    public boolean isWifiP2pEnabled() {
        return isWifiP2pEnabled;
    }

    public boolean isGroupFormed() {
        return groupInfo.groupFormed;
    }

    public boolean isGroupOwner() {
        return groupInfo.isGroupOwner;
    }

    public @NonNull InetAddress getGroupOwnerAddress() {
        return groupInfo.groupOwnerAddress;
    }

    public void performHandshakeWithSelectedPeerDevice() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Starting handshake");
        }
    }

    public static void copyToOutputStream(@NonNull InputStream inputStream,
                                          @NonNull OutputStream outputStream) throws IOException {
        byte[] bufferForBytes = new byte[1024];
        int bytesRead;

        Log.d(TAG, "Copying to OutputStream...");
        while ((bytesRead = inputStream.read(bufferForBytes)) != -1) {
            outputStream.write(bufferForBytes, 0, bytesRead);
        }

        outputStream.close();
        inputStream.close();
    }

    public void stopWifiDirectManager(boolean isChannelOpen) {
        if (isChannelOpen) {
            closeChannel();
        } else {
            disconnect();
        }

        unregisterWifiDirectBroadcastReceiver();
    }

    public void disconnect() {
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason: " + reasonCode);
                closeChannel();
            }

            @Override
            public void onSuccess() {
                Log.d(TAG, "Disconnect successful");
                closeChannel();
            }
        });
    }

    private void closeChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            channel.close();
        }
    }

    public @NonNull String getErrorMessage(int reason) {
        switch (reason) {
            case WifiP2pManager.ERROR:
                return "Internal error";
            case WifiP2pManager.BUSY:
                return "Framework busy, unable to service request";
            case WifiP2pManager.P2P_UNSUPPORTED:
                return "P2P unsupported on this device";

            default:
                return ("Unknown error code - " + reason);
        }
    }

    public static @NonNull String getDeviceStatus(int status) {

        if (BuildConfig.DEBUG) Log.d(TAG, "Peer Status: " + status);
        switch (status) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";

            default:
                return "Unknown";
        }
    }

    public interface Callbacks {
        void onUserDeviceDetailsAvailable(@Nullable WifiP2pDevice userDevice);

        void onConnectionToPeersLost();

        void updateListOfAvailablePeers(@NonNull WifiP2pDeviceList peers);

        void addReceivedMessage(String message);

        void addSentMessage(String message);

        void onSuccessBroadcastingMessage();
    }

    private class PeerTransmissionState {
        private long lastTimestamp;
        private int pktCount;

        public PeerTransmissionState(long lastTimestamp, int pktCount) {
            this.lastTimestamp = lastTimestamp;
            this.pktCount = pktCount;
        }

        public long getLastTimestamp() {
            return lastTimestamp;
        }

        public int getPktCount() {
            return pktCount;
        }

        public void setLastTimestamp(long lastTimestamp) {
            this.lastTimestamp = lastTimestamp;
        }

        public void setPktCount(int pktCount) {
            this.pktCount = pktCount;
        }
    }

    private static class DataPacket {

        //Keys must not be longer than 9 characters
        public final static String KEY_SENDER_ID = "device_id";
        public final static String KEY_TIMESTAMP = "timestamp";
        public final static String KEY_PKT_COUNT = "pkt_count";
        public final static String KEY_DESTINATION = "d";
        public final static String KEY_MESSAGE = "message";

        private String senderId;
        private long timestamp;
        private int pktCount;
        private String destination;
        private String message;

        public DataPacket(String senderId, String timestamp, String pktCount,
                          String destination, String message) {
            this.senderId = senderId;
            this.timestamp = Long.parseLong(timestamp);
            this.pktCount = Integer.parseInt(pktCount);
            this.destination = (destination == null) ? "" : destination;
            this.message = message;
        }

        public String getSenderId() {
            return senderId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getPktCount() {
            return pktCount;
        }

        public String getDestination() {
            return destination;
        }

        public String getMessage() {
            return message;
        }
    }
}
