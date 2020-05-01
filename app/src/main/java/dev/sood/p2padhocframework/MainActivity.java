package dev.sood.p2padhocframework;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import static dev.sood.p2padhocframework.WifiDirectManager.INITIAL_COUNT;

public class MainActivity extends AppCompatActivity implements
        WifiDirectManager.Callbacks {

    private WifiDirectManager wifiDirectManager;
    private WifiManager wifiManager;

    private List<String> recvMsgs = new ArrayList<>();

    int msgCount = INITIAL_COUNT;
    private List<String> sentMsgs = new ArrayList<>();

    private ListView listRecvMsg, listSentMsg;

    private ArrayAdapter<String> adapterRecMsg, adapterSentMsg;

    private EditText edtTextAddMssg, edtTextDestination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if(androidId == null) {
            androidId = "randomId:"+(1001*Math.random());
        }

        wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiDirectManager = new WifiDirectManager(this, androidId);

        listRecvMsg = findViewById(R.id.list_recv_msg);
        listSentMsg = findViewById(R.id.list_sent_msg);
        adapterRecMsg = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, recvMsgs);
        adapterSentMsg = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sentMsgs);
        listRecvMsg.setAdapter(adapterRecMsg);
        listSentMsg.setAdapter(adapterSentMsg);

        Button btnDiscoverMessages = findViewById(R.id.btn_discover_pkt);
        btnDiscoverMessages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiDirectManager.discoverPeerPackets();
            }
        });

        Button btnQueueMsg = findViewById(R.id.btn_queue_msg);
        edtTextAddMssg = findViewById(R.id.edt_txt_message);
        edtTextDestination = findViewById(R.id.edt_txt_destination);
        btnQueueMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String destination = edtTextDestination.getText().toString();
                String message = edtTextAddMssg.getText().toString();
                wifiDirectManager.addMessageToBroadcastQueue(msgCount, destination, message);
                msgCount++;
                edtTextDestination.setText("");
                edtTextAddMssg.setText("");
            }
        });

        Button btnBroadcast = findViewById(R.id.btn_send_pkt);
        btnBroadcast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiDirectManager.broadcastPackets();
            }
        });
    }

    @Override
    public void addSentMessage(String message) {
        sentMsgs.add(message);
        adapterSentMsg.notifyDataSetChanged();
    }

    @Override
    public void addReceivedMessage(String message) {
        recvMsgs.add(message);
        adapterRecMsg.notifyDataSetChanged();
        Log.d("WifiDirectManager", "Added message: "+message);
    }

    @Override
    public void onSuccessBroadcastingMessage(){
        msgCount++;
        edtTextAddMssg.setText("");
    }

    @Override
    public void onUserDeviceDetailsAvailable(@Nullable WifiP2pDevice userDevice) {

    }

    @Override
    public void onConnectionToPeersLost() {

    }

    @Override
    public void updateListOfAvailablePeers(@NonNull WifiP2pDeviceList peers) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!wifiManager.isWifiEnabled()) {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } else {
            wifiDirectManager.startWifiDirectManager();
        }
    }
}
