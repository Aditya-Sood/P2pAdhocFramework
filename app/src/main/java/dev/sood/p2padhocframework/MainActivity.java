package dev.sood.p2padhocframework;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.os.Bundle;
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
    private List<String> sentMsgs = new ArrayList<>();

    private ListView listRecvMsg, listSentMsg;

    private ArrayAdapter<String> adapterRecMsg, adapterSentMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WifiDirectManager wifiDirectManager = new WifiDirectManager(this);
        wifiDirectManager.startWifiDirectManager();

        listRecvMsg = findViewById(R.id.list_recv_msg);
        listSentMsg = findViewById(R.id.list_sent_msg);
        adapterRecMsg = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, recvMsgs);
        adapterSentMsg = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sentMsgs);
        listRecvMsg.setAdapter(adapterRecMsg);
        listSentMsg.setAdapter(adapterSentMsg);

        Button btnCreateGroup = findViewById(R.id.btn_create_group);
        btnCreateGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiDirectManager.createGroup();
            }
        });

        Button btnGetGroupInfo = findViewById(R.id.btn_get_group_info);
        btnGetGroupInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiDirectManager.getGroupInfo();
            }
        });

        wifiDirectManager.setDnsSdServiceResponseListener();
        Button btnGetPeerInfo = findViewById(R.id.btn_get_peer_info);
        btnGetPeerInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearReceivedMessages();
                wifiDirectManager.discoverDnsServices();
            }
        });

        Button btnAddServ = findViewById(R.id.btn_add_serv);
        final EditText edtTextAddMssg = findViewById(R.id.edt_txt_message);
        btnAddServ.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Toast.makeText(MainActivity.this, edtTextAddMssg.getText(), Toast.LENGTH_SHORT).show();
                wifiDirectManager.addMessage(edtTextAddMssg.getText().toString());
                addSentMessage(edtTextAddMssg.getText().toString());
                edtTextAddMssg.setText("");
            }
        });

        Button btnRemAllServ = findViewById(R.id.btn_rmv_serv);
        btnRemAllServ.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiDirectManager.removeAllMessages();
            }
        });

    }

    public void addSentMessage(String message) {
        sentMsgs.add(message);
        adapterSentMsg.notifyDataSetChanged();
    }

    @Override
    public void addReceivedMessage(String message) {
        recvMsgs.add(message);
        adapterRecMsg.notifyDataSetChanged();
    }

    @Override
    public void clearReceivedMessages() {
        adapterRecMsg.clear();
//        adapterRecMsg.notifyDataSetChanged();
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
