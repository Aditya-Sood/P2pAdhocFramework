package dev.sood.p2padhocframework;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

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

    private Map<String, PeerBroadcastState> mapPeerState = new HashMap<>();
    private Map<String, Queue<BroadcastMessage>> mapBroadcastMsgQueue = new HashMap<>();

    public WifiDirectManager(@NonNull Activity activity, String androidId) {
        this.activity = activity;
        this.callbacks = (Callbacks) activity;
        this.androidId = androidId;

        mapBroadcastMsgQueue.put(androidId, new LinkedList<BroadcastMessage>());
    }

    /* Initialisations for using the WiFi P2P API */
    public void startWifiDirectManager() {
        manager = (WifiP2pManager) activity.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(activity, getMainLooper(), null);
        registerWifiDirectBroadcastReceiver();
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

    public void addMessageToBroadcastQueue(final int sendCount, final String message) {
        mapBroadcastMsgQueue.get(androidId)
                .add(new BroadcastMessage(androidId, System.nanoTime()+"", sendCount+"", message));
    }

    public void broadcastMessages() {
        removeAllCurrentBroadcasts();
    }

    //TODO?
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
        for(Map.Entry<String, Queue<BroadcastMessage>> mapEntry : mapBroadcastMsgQueue.entrySet()) {
            if(mapEntry.getValue().size() > 0) {
                BroadcastMessage broadcastMessage = mapEntry.getValue().remove();
                Log.d(WifiDirectManager.TAG, "Prepping: " + broadcastMessage.toString());
                WifiP2pDnsSdServiceInfo serviceInfo = getServiceInfo(broadcastMessage);
                WifiDirectManager.this.addLocalService(serviceInfo);

                if(mapEntry.getKey().equals(androidId)) {
                    callbacks.addSentMessage(broadcastMessage.getMessage());
                }
            }
        }
    }

    public WifiP2pDnsSdServiceInfo getServiceInfo(BroadcastMessage broadcastMessage) {
        Map<String, String> serviceInfoMap = new HashMap<String, String>();
        serviceInfoMap.put("txtvers", "0.1"); // For version compatibility
        serviceInfoMap.put(BroadcastMessage.DEVICE_ID, broadcastMessage.getDeviceId());
        serviceInfoMap.put(BroadcastMessage.TIMESTAMP, broadcastMessage.getTimestamp()+"");
        serviceInfoMap.put(BroadcastMessage.MSG_COUNT, broadcastMessage.getMsgCount()+"");
        serviceInfoMap.put(BroadcastMessage.MESSAGE, broadcastMessage.getMessage());

        WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE_NAME, "_bonjour._tcp", serviceInfoMap);

        return serviceInfo;
    }

    public void addLocalService(final WifiP2pServiceInfo servInfo) {
        manager.addLocalService(channel, servInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(WifiDirectManager.TAG, "Added service " + servInfo.toString());
                Toast.makeText(activity, "Added local service - " + servInfo.toString(), Toast.LENGTH_SHORT).show();
                //TODO - remove message on successful broadcast?
//                callbacks.onSuccessBroadcastingMessage();
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

                Log.d(WifiDirectManager.TAG, fullDomainName+" : "+txtRecordMap.get(BroadcastMessage.DEVICE_ID)+" - "+txtRecordMap.get(BroadcastMessage.MESSAGE));

                String peerDeviceId = txtRecordMap.get(BroadcastMessage.DEVICE_ID);
                long peerTimestamp = Long.parseLong(txtRecordMap.get(BroadcastMessage.TIMESTAMP));
                int peerSendCount = Integer.parseInt(txtRecordMap.get(BroadcastMessage.MSG_COUNT));
                String peerMessage = txtRecordMap.get(BroadcastMessage.MESSAGE);

                if(peerDeviceId.equals(androidId)) {
                    Log.d(WifiDirectManager.TAG, "Cyclic broadcast, ignore message");
                    return;
                }

                if(mapPeerState.containsKey(peerDeviceId)) {
                    if((peerSendCount == 0 && peerTimestamp <= mapPeerState.get(peerDeviceId).getLastTimestamp()) || (peerSendCount > 0 && peerSendCount <= mapPeerState.get(peerDeviceId).getMsgCount())) {
                        Log.d(WifiDirectManager.TAG, "Stale message ignored");
                        return;
                    } else {
                        mapPeerState.get(peerDeviceId).setLastTimestamp(peerTimestamp);
                        mapPeerState.get(peerDeviceId).setMsgCount(peerSendCount);
                    }
                } else {
                    mapPeerState.put(peerDeviceId, new PeerBroadcastState(peerTimestamp, peerSendCount));
                    mapBroadcastMsgQueue.put(peerDeviceId, new LinkedList<BroadcastMessage>());
                }

                mapBroadcastMsgQueue.get(peerDeviceId)
                        .add(new BroadcastMessage(peerDeviceId, peerTimestamp+"", peerSendCount+"", peerMessage));

                callbacks.addReceivedMessage(peerDeviceId+": "+peerMessage+"\nTimestamp: "+peerTimestamp);

                Log.d(WifiDirectManager.TAG, "Added message: "+peerDeviceId+" - "+peerMessage);

                Toast.makeText(activity, "Received message from Android ID:"+peerDeviceId+"\nMessage"
                        +peerSendCount+" :"+peerMessage+"\nTimestamp:"+peerTimestamp, Toast.LENGTH_LONG).show();
            }
        });

        addServiceRequest(WifiP2pDnsSdServiceRequest.newInstance(SERVICE_INSTANCE_NAME, "_bonjour._tcp"));
    }

    /**
     * Discovers all DNS service broadcasts - and hence the peer messages*/
    public void discoverDnsServices() {
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

    public void discoverPeerDevices() {
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
//                displayToast(R.string.discovery_initiated, Toast.LENGTH_SHORT);
            }

            @Override
            public void onFailure(int reason) {
                String errorMessage = getErrorMessage(reason);
//                Log.d(TAG, activity.getString(R.string.discovery_failed) + ": " + errorMessage);
//                displayToast(R.string.discovery_failed, Toast.LENGTH_SHORT);
            }
        });
    }

    /* From KiwixWifiP2pBroadcastReceiver.P2pEventListener callback-interface*/
    @Override
    public void onWifiP2pStateChanged(boolean isEnabled) {
        this.isWifiP2pEnabled = isEnabled;

        if (!isWifiP2pEnabled) {
//            displayToast(R.string.discovery_needs_wifi, Toast.LENGTH_SHORT);
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
//        if (isConnected) {
//            // Request connection info about the wifi p2p group formed upon connection
//            manager.requestConnectionInfo(channel, this);
//        } else {
//            // Not connected after connection change -> Disconnected
//            callbacks.onConnectionToPeersLost();
//        }

        /*manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                if(group != null) {
//                    Toast.makeText(activity, "Group info available\nSSID:"+group.getNetworkName()+"\nPassphrase:"+group.getPassphrase()
//                            +"\nMAC:\nConnectivity Status:", Toast.LENGTH_LONG).show();

                    //Group is ready -> add to a wifip2pdnsSdserviceinfo

                    JSONObject serviceInfoJson;
                    String serviceInfoSerialised = "";
                    try {
                        serviceInfoJson = new JSONObject("{ \"ssid\":\""+ group.getNetworkName() +
                                "\", \"passphrase\":\"" + group.getPassphrase() +"\", \"message\":\"You made it!\"}");
                        serviceInfoSerialised = serviceInfoJson.toString();

                        Log.d(WifiDirectManager.TAG, "Self group info: " + serviceInfoSerialised);
                    } catch (JSONException e) {
                        Log.e(WifiDirectManager.TAG, e.getMessage());
                    }

                    Map<String, String> serviceInfoMap = new HashMap<String, String>();
                    serviceInfoMap.put("details_json", serviceInfoSerialised);
                    WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("gandalfTestInstance-" + group.getNetworkName(), "_bonjour._tcp", serviceInfoMap);

                    WifiDirectManager.this.addLocalService(serviceInfo);
                }
                else {
//                    Toast.makeText(activity, "Group not formed yet", Toast.LENGTH_LONG).show();
                }

            }
        });*/


        Log.d(WifiDirectManager.TAG, "P2p Connnection Changed: isConnected " + isConnected);
    }

    @Override
    public void onDeviceChanged(@Nullable WifiP2pDevice userDevice) {
        // Update UI with wifi-direct details about the user device
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
        } else {
//            displayToast(R.string.severe_loss_error, Toast.LENGTH_LONG);
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

    public void sendToDevice(@NonNull WifiP2pDevice senderSelectedPeerDevice) {
    }

    public void connect() {
        WifiP2pConfig config = new WifiP2pConfig();
//        config.deviceAddress = senderSelectedPeerDevice.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // UI updated from broadcast receiver
            }

            @Override
            public void onFailure(int reason) {
                String errorMessage = getErrorMessage(reason);
//                Log.d(TAG, activity.getString(R.string.connection_failed) + ": " + errorMessage);
//                displayToast(R.string.connection_failed, Toast.LENGTH_LONG);
            }
        });
    }

    public void performHandshakeWithSelectedPeerDevice() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Starting handshake");
        }
//        peerGroupHandshakeAsyncTask = new PeerGroupHandshakeAsyncTask(this);
//        peerGroupHandshakeAsyncTask.execute();
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
//        Log.d(LocalFileTransferActivity.TAG, "Both streams closed");
    }

    public void setClientAddress(@Nullable InetAddress clientAddress) {
        if (clientAddress == null) {
            // null is returned only in case of a failed handshake
//            displayToast(R.string.device_not_cooperating, Toast.LENGTH_LONG);
//            callbacks.onFileTransferComplete();
//            return;
        }

        // If control reaches here, means handshake was successful
//        selectedPeerDeviceInetAddress = clientAddress;
//        startFileTransfer();
    }


    public void stopWifiDirectManager() {
//        if (isFileSender) {
//            closeChannel();
//        } else {
//            disconnect();
//        }

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

    public static @NonNull String getFileName(@NonNull Uri fileUri) {
        String fileUriString = fileUri.toString();
        // Returns text after location of last slash in the file path
        return fileUriString.substring(fileUriString.lastIndexOf('/') + 1);
    }

    public void displayToast(int stringResourceId, @NonNull String templateValue, int duration) {
//        showToast(activity, activity.getString(stringResourceId, templateValue), duration);
    }

    public void displayToast(int stringResourceId, int duration) {
//        showToast(activity, stringResourceId, duration);
    }

    public interface Callbacks {
        void onUserDeviceDetailsAvailable(@Nullable WifiP2pDevice userDevice);

        void onConnectionToPeersLost();

        void updateListOfAvailablePeers(@NonNull WifiP2pDeviceList peers);

        void addReceivedMessage(String message);

        void addSentMessage(String message);

        void onSuccessBroadcastingMessage();
    }

    private class PeerBroadcastState {
        private long lastTimestamp;
        private int msgCount;

        public PeerBroadcastState(long lastTimestamp, int msgCount) {
            this.lastTimestamp = lastTimestamp;
            this.msgCount = msgCount;
        }

        public long getLastTimestamp() {
            return lastTimestamp;
        }

        public int getMsgCount() {
            return msgCount;
        }

        public void setLastTimestamp(long lastTimestamp) {
            this.lastTimestamp = lastTimestamp;
        }

        public void setMsgCount(int msgCount) {
            this.msgCount = msgCount;
        }
    }

    private static class BroadcastMessage {

        //Keys must not be longer than 9 characters
        public final static String DEVICE_ID = "device_id";
        public final static String TIMESTAMP = "timestamp";
        public final static String MSG_COUNT = "msg_count";
        public final static String MESSAGE = "message";

        private String deviceId;
        private long timestamp;
        private int msgCount;
        private String message;

        public BroadcastMessage(String deviceId, String timestamp, String msgCount, String message) {
            this.deviceId = deviceId;
            this.timestamp = Long.parseLong(timestamp);
            this.msgCount = Integer.parseInt(msgCount);
            this.message = message;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getMsgCount() {
            return msgCount;
        }

        public String getMessage() {
            return message;
        }
    }
}
