package dev.sood.p2padhocframework;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements
        WifiDirectManager.Callbacks {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WifiDirectManager wifiDirectManager = new WifiDirectManager(this);
        wifiDirectManager.startWifiDirectManager();

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
                wifiDirectManager.discoverDnsServices();
            }
        });

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
