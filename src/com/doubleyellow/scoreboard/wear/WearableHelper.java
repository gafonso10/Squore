/*
 * Copyright (C) 2020  Iddo Hoeve
 *
 * Squore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.doubleyellow.scoreboard.wear;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;

import androidx.annotation.NonNull;

import com.doubleyellow.android.view.ViewUtil;
import com.doubleyellow.scoreboard.Brand;
import com.doubleyellow.scoreboard.bluetooth.BTMethods;
import com.doubleyellow.scoreboard.bluetooth.BTRole;
import com.doubleyellow.scoreboard.main.ScoreBoard;
import com.doubleyellow.util.ListUtil;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.wearable.intent.RemoteIntent;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static com.doubleyellow.scoreboard.main.ScoreBoard.m_blueToothRole;

public class WearableHelper {
    private static final String TAG = "SB." + WearableHelper.class.getSimpleName();

    private static final boolean m_bStartAppOnWear = false;

    public WearableHelper(ScoreBoard scoreBoard) {
      //Wearable.getDataClient      (this).addListener(onDataChangedListener);
        onMessageReceivedListener = new MessageListener(scoreBoard);

        Wearable.getMessageClient   (scoreBoard).addListener(onMessageReceivedListener);
        Wearable.getCapabilityClient(scoreBoard).addListener(onCapabilityChangedListener, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE);

        // Initial request for devices with our capability, aka, our Wear app installed.
        findWearDevicesWithApp(scoreBoard);

        // Initial request for all Wear devices connected (with or without our capability).
        // Additional Note: Because there isn't a listener for ALL Nodes added/removed from network
        // that isn't deprecated, we simply update the full list when the Google API Client is
        // connected and when capability changes come through in the onCapabilityChanged() method.
        findAllWearDevices(scoreBoard);

        if ( ViewUtil.isWearable(scoreBoard) ) {
        } else {
            // TODO: only once!! not on rotate
            //sendMessageToWearables(this, DataLayerListenerService.START_ACTIVITY_PATH, "");
            if ( m_bStartAppOnWear ) {
                sendDataToWearables(scoreBoard, DataLayerListenerService.START_ACTIVITY_PATH, "THE_TIME_TEST", "");
            }
        }
    }

    public void onPause(ScoreBoard scoreBoard) {
        //Wearable.getDataClient      (this).removeListener(onDataChangedListener);
        Wearable.getMessageClient   (scoreBoard).removeListener(onMessageReceivedListener);
        Wearable.getCapabilityClient(scoreBoard).removeListener(onCapabilityChangedListener);
    }

    static final String BRAND_PATH              = "/" + Brand.brand;
    /** variables used to not SEND a message back while handling an INCOMING message */
    private static boolean m_bHandlingWearableMessageInProgress = false;

    private MessageListener onMessageReceivedListener = null;
    private static class MessageListener implements MessageClient.OnMessageReceivedListener {
        ScoreBoard m_scoreBoard = null;
        private MessageListener(ScoreBoard scoreBoard) {
            m_scoreBoard = scoreBoard;
        }
        @Override public void onMessageReceived(@NonNull MessageEvent messageEvent) {
            byte[] baData = messageEvent.getData();
            String sData  = new String(baData);
            String sourceNodeId = messageEvent.getSourceNodeId();
            // messageEvent.getPath() // typically BRAND_PATH
            Log.d(TAG, String.format("reqid %d from %s received: %s", messageEvent.getRequestId(), sourceNodeId , sData));

            // received a message from the handheld-wearable counterpart: handle change here
            m_bHandlingWearableMessageInProgress = true;
            m_scoreBoard.interpretReceivedMessage(sData, true);
            m_bHandlingWearableMessageInProgress = false;
        }
    };

    private Set<Node> m_lWearNodesWithApp;
    private List<Node> m_lAllConnectedNodes;
    private CapabilityClient.OnCapabilityChangedListener onCapabilityChangedListener = new CapabilityClient.OnCapabilityChangedListener() {
        // Is triggered when capabilities change (install/uninstall wear app). Also on start?
        @Override public void onCapabilityChanged(@NonNull CapabilityInfo capabilityInfo) {
            Log.d(TAG, "capabilityInfo: " + capabilityInfo); // e.g : [Node{Samsung Galaxy S7, id=fbc3c3e2, hops=1, isNearby=true}]
            m_lWearNodesWithApp = capabilityInfo.getNodes();
/*
            // Because we have an updated list of devices with/without our app, we need to also update our list of active Wear devices.
            if ( isWearable() == false ) {
                findAllWearDevices();

                considerWearableNodesAndTakeAction();
            }
*/
        }
    };

    private void findWearDevicesWithApp(final Context ctx) {
        Log.d(TAG, "findWearDevicesWithApp()");

        CapabilityClient capabilityClient = Wearable.getCapabilityClient(ctx);
        String sCheckCapability = ViewUtil.isWearable(ctx) ? CAPABILITY_APP_HANDHELD : CAPABILITY_APP_WEAR;
        Task<CapabilityInfo> capabilityInfoTask = capabilityClient.getCapability(sCheckCapability, CapabilityClient.FILTER_ALL);

        capabilityInfoTask.addOnCompleteListener(new OnCompleteListener<CapabilityInfo>() {
            @Override public void onComplete(Task<CapabilityInfo> task) {
                if ( task.isSuccessful() ) {
                    CapabilityInfo capabilityInfo = task.getResult();
                    m_lWearNodesWithApp = capabilityInfo.getNodes();

                    Log.d(TAG, "Capable Nodes: " + m_lWearNodesWithApp); // [Node{Carlyle HR 1748, id=7a6a0c72, hops=1, isNearby=true}]

                    considerWearableNodesAndTakeAction(ctx);
                } else {
                    Log.w(TAG, "Capability request failed to return any results.");
                }
            }
        });
    }
    private void findAllWearDevices(final Context ctx) {
        Log.d(TAG, "findAllWearDevices()");

        NodeClient nodeClient = Wearable.getNodeClient(ctx);
        Task<List<Node>> NodeListTask = nodeClient.getConnectedNodes();

        NodeListTask.addOnCompleteListener(new OnCompleteListener<List<Node>>() {
            @Override public void onComplete(Task<List<Node>> task) {
                if ( task.isSuccessful() ) {
                    Log.d(TAG, "Node request succeeded.");
                    m_lAllConnectedNodes = task.getResult();
                } else {
                    Log.w(TAG, "Node request failed to return any results.");
                }

                considerWearableNodesAndTakeAction(ctx);
            }
        });
    }
    // Name of capability listed in Wear app's wear.xml.
    // IMPORTANT NOTE: This should be named differently than your Phone app's capability.
    private static final String CAPABILITY_APP_WEAR     = "verify_remote_app_wear";
    private static final String CAPABILITY_APP_HANDHELD = "verify_remote_app_handheld";

    private void considerWearableNodesAndTakeAction(Context context) {
        Log.d(TAG, "verifyNodeAndUpdateUI()");

        if ((m_lWearNodesWithApp == null) || (m_lAllConnectedNodes == null)) {
            Log.d(TAG, "Waiting on Results for both 'connected nodes' and 'nodes with app'");
        } else if ( m_lAllConnectedNodes.isEmpty() ) {
/*
        } else if ( m_lWearNodesWithApp.isEmpty() ) {
            Log.d(TAG, MISSING_ALL_MESSAGE);
            openPlayStoreOnWearDevicesWithoutApp();
*/
        } else if ( m_lWearNodesWithApp.size() < m_lAllConnectedNodes.size() ) {
            //openOrSuggestInstallOnWearDevices();
        } else {
            if ( ViewUtil.isWearable(context) ) {
                //pullOrPushMatchOverBluetoothWearable("Handheld");
                new SendMessageToWearableTask(context).execute(BRAND_PATH, BTMethods.openSuggestMatchSyncDialogOnOtherPaired.toString());
            } else {
                if ( m_bStartAppOnWear ) {
                    openOrSuggestInstallOnWearDevices(context);
                }
            }
        }
    }

    public void sendMatchFromToWearable(String sJson, Context ctx) {
        if ( m_wearableRole == null ) {
            m_wearableRole = WearRole.Equal;
        }
        //sendMessageToWearables(ctx, BRAND_PATH, sJson.length() + ":" + sJson); // don't : bypass check and start async task directly
        new SendMessageToWearableTask(ctx).execute(BRAND_PATH, sJson.length() + ":" + sJson);
    }

    public void sendMessageToWearablesUnchecked(Context context, String sMessage) {
        new SendMessageToWearableTask(context).execute(BRAND_PATH, sMessage);
    }
    public void sendMessageToWearables(Context context, String sMessage) {
        sendMessageToWearables(context, BRAND_PATH, sMessage);
    }
    private void sendMessageToWearables(Context context, String sPath, String sMessage) {
        if ( BTRole.Equal.equals(m_blueToothRole) == false ) {
            // other manual set up bluetooth connection active
            Log.d(TAG, "Manually paired via bluetooth... Not sending to wearable");
        }
        if ( m_bHandlingWearableMessageInProgress ) {
            Log.d(TAG, "Not sending message " + sMessage + ". Still interpreting incoming message");
            return;
        }
        if ( sMessage.startsWith(BTMethods.requestCompleteJsonOfMatch.toString() ) )  {
            // independent of current wearable role, send anyways
        } else if ( m_wearableRole == null ) {
            Log.d(TAG, "No match has been exchanged. Not sending: " + sMessage);
            return;
        }
        new SendMessageToWearableTask(context).execute(sPath, sMessage);
    }
    private static class SendMessageToWearableTask extends AsyncTask<String, Void, String> {
        private MessageClient messageClient = null;
        private NodeClient    nodeClient    = null;

        private SendMessageToWearableTask(Context ctx) {
            messageClient = Wearable.getMessageClient(ctx);
            nodeClient    = Wearable.getNodeClient(ctx);
        }
        @Override protected String doInBackground(String[] objects) {
            if ( nodeClient == null ) {
                Log.d(TAG, "No instance of " + NodeClient.class.getName());
                return null;
            }
            if ( messageClient == null ) { return null; }

            try {
                Task<List<Node>> nodeListTask = nodeClient.getConnectedNodes();
                List<Node> nodes = Tasks.await(nodeListTask);

                if ( ListUtil.isEmpty(nodes) ) { return null; }
                for( Node node : nodes ) {
                    String sPath    = objects[0];
                    String sMessage = objects[1];
                    Task<Integer> sendMessageTask = messageClient.sendMessage(node.getId(), sPath, sMessage.getBytes());
                    Log.d(TAG, "Send message :" + sMessage);
                    Integer requestId = Tasks.await(sendMessageTask);
                    Log.d(TAG, "Send result: " + requestId); // just an (for every call increasing) integer. Same number as messageEvent.getRequestId() on receiving end
                }
            } catch (Exception exception) {
                Log.e(TAG, "Task failed: " + exception);
            }
            return null;
        }
    }

    private static boolean m_bAttemptToStartOnWearableDone = false;
    private void openOrSuggestInstallOnWearDevices(Context context) {
        Log.d(TAG, "openOrSuggestInstallOnWearDevices()");
        if ( ListUtil.isEmpty(m_lAllConnectedNodes)) {
            return;
        }
        if ( m_bAttemptToStartOnWearableDone ) { return; }
        m_bAttemptToStartOnWearableDone = true;
/*
<activity android:name="com.google.android.finsky.activities.MarketDeepLinkHandlerActivity"
          android:enabled="true"
          android:exported="true"
          android:excludeFromRecents="true"
          android:visibleToInstantApps="true">

            <meta-data android:name="instantapps.clients.allowed" android:value="true" />

            <intent-filter>
                <action android:name="android.intent.action.VIEW" />

                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="market" android:host="details" />
            </intent-filter>
        </activity>
*/

        String sMarketURL = "market://details" + "?id=" + context.getPackageName();

        for (Node node : m_lAllConnectedNodes) {
            String sAppURL = "ihoeve://" + Brand.brand.toString().toLowerCase();
            sAppURL = "market://squore.double-yellow.be"; // TODO
            sAppURL = Brand.brand.getBaseURL() + "/show/20"; // TODO:

            String sURL = sAppURL;
            if ( m_lWearNodesWithApp.contains(node) == false ) {
                sURL = sMarketURL;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(Uri.parse(sURL));
            RemoteIntent.startRemoteActivity(context, intent, mResultReceiver, node.getId());
        }
    }

    /**
     * if not set, don't send messages between devices, allowing both to keep score of a different match
     * If set to Equal, match has been exchanged deliberatly and try to keep them in sync from now on.
     **/
    private WearRole m_wearableRole = null;

    public void setWearableRole(WearRole role) {
        m_wearableRole = role;
    }

    // Result from sending RemoteIntent to wear device(s) to open app in play/app store.
    private final ResultReceiver mResultReceiver = new ResultReceiver(new Handler()) {
        @Override protected void onReceiveResult(int resultCode, Bundle resultData) {
            Log.d(TAG, "onReceiveResult: " + resultCode);

            if ( resultCode == RemoteIntent.RESULT_OK ) {
                Log.d(TAG, "Play Store Request to Wear device successful.");
            } else if ( resultCode == RemoteIntent.RESULT_FAILED ) {
                Log.d(TAG, "Play Store Request Failed. Wear device(s) may not support Play Store,  that is, the Wear device may be version 1.0.");
            } else {
                throw new IllegalStateException("Unexpected result " + resultCode);
            }
        }
    };

    /** can e.g. be used to send a START_ACTIVITY message to a subclass of WearableListenerService */
    private void sendDataToWearables(Context ctx, String sPath, String sAssetKey, String sMessage) {
        Asset asset = Asset.createFromBytes(sMessage.getBytes());
        PutDataMapRequest dmRequest = PutDataMapRequest.create(sPath); // ASSET_PATH
        DataMap dataMap = dmRequest.getDataMap();
        dataMap.putAsset(sAssetKey, asset);
        dataMap.putLong("time", new Date().getTime());
        PutDataRequest pdRequest = dmRequest.asPutDataRequest();
        pdRequest.setUrgent();

        DataClient dataClient = Wearable.getDataClient(ctx);
        Task<DataItem> dataItemTask = dataClient.putDataItem(pdRequest);

        dataItemTask.addOnSuccessListener(new OnSuccessListener<DataItem>() {
            @Override public void onSuccess(DataItem dataItem) {
                Log.d(TAG, "Sending was successful: " + dataItem);
            }
        });
    }
/*
    private DataClient.OnDataChangedListener onDataChangedListener = new DataClient.OnDataChangedListener() {
        @Override public void onDataChanged(@NonNull DataEventBuffer dataEvents) {
            Log.d(TAG, "dataEventBuffer: " + dataEvents);
            for (DataEvent event : dataEvents) {
                DataItem dataItem = event.getDataItem();
                Map<String, DataItemAsset> assets = dataItem.getAssets();
                int type = event.getType();
                switch (type) {
                    case DataEvent.TYPE_CHANGED:
                        String path = dataItem.getUri().getPath();
                        if ( ASSET_PATH.equals(path) ) {
                            DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                            DataMap dataMap = dataMapItem.getDataMap();
                            Asset asset = dataMap.getAsset(ASSET_KEY);
                            // TODO: handle the asset

                            // Optionally Send message to node that created the data item
                            Uri uri = event.getDataItem().getUri();
                            String nodeId = uri.getHost();
                            Wearable.getMessageClient(ScoreBoard.this).sendMessage(nodeId, DATA_ITEM_RECEIVED_PATH, uri.toString().getBytes());
                        } else {
                            Log.d(TAG, "Unrecognized path: " + path);
                        }
                        break;
                    case DataEvent.TYPE_DELETED:
                        break;
                }
            }
        }
    };
*/
}
