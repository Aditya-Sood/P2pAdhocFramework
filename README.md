# P2pAdhocFramework

The WiFi P2P library of Android (`WifiP2pManager`) does not support devices operating in ad-hoc mode, because of which devices cannnot form and participate in a self-deployed, ad-hoc P2P communication network. This renders devices unable to communicate with each other without the presence of external infrastructure (like a cellular or WiFi network).

The aim of this framework is to circumvent the lack of an ad-hoc mode by providing a framework which allows devices to participate in a self-made ad-hoc network, without need for any external infrastructure. Additionally, the framework doesn't require user devices to be rooted, allowing for easy adoption of the solution.

---

An instance of the `WifiDirectManager` class is the entry point for the library. Any application using the framework will use an instance of the class for performing various operations like transmitting and discovering messages from peer devices.

Brief overview of how the framework works:
* The fundamental unit of communication is `DataPacket`, and devices communicate by sending the packets to all of their peer devices.

* `PeerTransmissionState` class is used to maintain state of the peer device at the time of sending the packet, which comes in handy for identifying stale packets circulating in the network.

* The framework utilises the DNS-SD local service (which is supported by Android) as its communication backbone, with packets being broadcasted to neighbouring devices as local services, and discoverd by those peer devices in the same manner.

Steps in using the framework:
1. Obtain an instance of the `WifiDirectManager` using the constructor, which requires a reference to the activity using the instance, and a unique identifier of the user device such as its ANDROID_ID. Obtaining one in the `onCreate()` phase is suggested, to avoid creation of multiple instances of the class.

2. Set up the device for participating in the network:
    1. Check if the WiFi on the device is enabled (can be done using the `isWifiEnabled()` method of the `WifiManager` API). If not, ask the user to enable it either through a alert dialog or sending them to the wireless settings page.
    2. Once enabled, call the `startWifiDirectManager()` on your `WifiDirectManager` instance to prepare the device for participating in the ad-hoc network. This performs operations such as registering a broadcast receiver, setting the response listeners, creating packet queues, etc.

(Both these operations can be performed in the `onResume()` phase, or atmost during the `onCreate()` phase)

3. Device is ready to participate in the network:
    * Queue messages with the `addMessageToBroadcastQueue()` method, and transmit them to peers using the `broadcastPackets()` method. (Messages can be broadcasted or sent to a particular destination.)
    * Discover messages from peer devices using the `discoverPeerPackets()` method

The `WifiDirectManager` instance will persist across the application going to background and coming back to foreground. A message is broadcasted whenever the application comes to the foreground, informing the network of the device being online again.














