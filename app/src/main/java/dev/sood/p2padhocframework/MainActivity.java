package dev.sood.p2padhocframework;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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

public class MainActivity extends AppCompatActivity implements
        WifiDirectManager.Callbacks {

    private List<String> recvMsgs = new ArrayList<>();

    int msgCount = 0;
    private List<String> sentMsgs = new ArrayList<>();

    private ListView listRecvMsg, listSentMsg;

    private ArrayAdapter<String> adapterRecMsg, adapterSentMsg;

    private EditText edtTextAddMssg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if(androidId == null) {
            androidId = "randomId:"+(1001*Math.random());
        }

        final WifiDirectManager wifiDirectManager = new WifiDirectManager(this, androidId);
        wifiDirectManager.startWifiDirectManager();

        wifiDirectManager.setDnsSdServiceResponseListener();
        wifiDirectManager.removeAllCurrentBroadcasts();

        wifiDirectManager.addMessageToBroadcastQueue(msgCount, androidId+" online");
        msgCount++;
        wifiDirectManager.broadcastMessages();
        wifiDirectManager.discoverDnsServices();

        listRecvMsg = findViewById(R.id.list_recv_msg);
        listSentMsg = findViewById(R.id.list_sent_msg);
        adapterRecMsg = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, recvMsgs);
        adapterSentMsg = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sentMsgs);
        listRecvMsg.setAdapter(adapterRecMsg);
        listSentMsg.setAdapter(adapterSentMsg);

        Button btnDiscoverMessages = findViewById(R.id.btn_discover_msg);
        btnDiscoverMessages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //clearReceivedMessages()
                wifiDirectManager.discoverDnsServices();
            }
        });

        Button btnQueueMsg = findViewById(R.id.btn_queue_msg);
        edtTextAddMssg = findViewById(R.id.edt_txt_message);
        btnQueueMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Toast.makeText(MainActivity.this, edtTextAddMssg.getText(), Toast.LENGTH_SHORT).show();
                String message = edtTextAddMssg.getText().toString();
                wifiDirectManager.addMessageToBroadcastQueue(msgCount, message);
                msgCount++;
                edtTextAddMssg.setText("");
            }
        });

        Button btnBroadcast = findViewById(R.id.btn_broadcast);
        btnBroadcast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiDirectManager.broadcastMessages();
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

    //TODO - look at function caller
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
}
