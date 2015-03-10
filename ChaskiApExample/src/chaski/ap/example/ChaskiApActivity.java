/** This class serves to enables/disables an access point related Chaski services.
 * The App contains a toggle button to enable and disable a an access point and also
 * it has an update button to (re-) an access point with new SSID(started with *#)
 * If a client connects to the access point, the app displays the number of connected
 * Clients and their IP addresses   **/

package chaski.ap.example;

import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;
import chaski.api.ChaskiConstants;
import chaski.api.ChaskiService;
import chaski.api.util.Util;

public class ChaskiApActivity extends Activity {

	private static final String TAG = ChaskiApActivity.class.getName();

	private static final String PREFIX = "*#%Chaski";

	private static final String NOT_APPLICABLE = "N/A";
	
	private ScheduledThreadPoolExecutor stpeTriggerClients;

	final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			if (intent.getAction().equals(ChaskiConstants.AP_STATE_ACTION)) {

				int currentStateAp = intent.getIntExtra(
						ChaskiConstants.AP_STATE,
						ChaskiConstants.CONNECTION_ERROR_STATE);

				if(apStatesInitialized){
					handleApStateChanged(currentStateAp);
				}
			}

			// Getting the number of connected clients and their IP
			if (intent.getAction().equals(ChaskiConstants.CLIENTS_STATE_ACTION)) {
				if (intent
						.hasExtra(ChaskiConstants.KEY_STRING_ARRAY_OF_CLIENTS)) {

					String operationString = intent
							.getStringExtra(ChaskiConstants.KEY_OPERATION_STRING);
					Log.d(TAG, "operation String: " + operationString);

					String[] clients = intent
							.getStringArrayExtra(ChaskiConstants.KEY_STRING_ARRAY_OF_CLIENTS);

					handleClientsStateActionChanged(clients);

				}
			}

		}

	};

	private ToggleButton mToggleButtonAp;
	private Button mButtonUpdate;
	private TextView mTextViewApStatus, mTextViewApSSID,
			mTextViewNumberOfClients, mTextViewIpAddressesOfClients;

	private WifiManager mWifiManager;

	private ChaskiService mChaskiService;

	private boolean mBoundToChaskiService;

	private ServiceConnection chaskiServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {

			mBoundToChaskiService = true;

			Log.d(TAG, "Chaski service has been bound");

			mChaskiService = ((ChaskiService.MyBinder) binder).getService();
			
			initApStates();

			mToggleButtonAp.setEnabled(true);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

			Log.d(TAG, "Chaski service has been unbound");

			mToggleButtonAp.setEnabled(false);

			mBoundToChaskiService = false;

		}

	};

	private int mWifiApDisabled;

	private int mWifiApEnabled;

	private boolean apStatesInitialized;

	protected void handleClientsStateActionChanged(String[] clients) {

		// Displaying the number of connected clients
		mTextViewNumberOfClients.setText(String.valueOf(clients.length));

		String values = "";

		if (clients.length > 0) {

			for (int i = 0; i < clients.length; i++) {
				values += clients[i];

				if (i < (clients.length - 1)) {
					values += ";" + "\n";
				}
			}

		} else {
			values = NOT_APPLICABLE;
		}

		// Displaying the IP of connected clients
		mTextViewIpAddressesOfClients.setText(values);

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_chaski_ap);

		// Getting the WiFi Services
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

		// The following code checks the Chaski service is running or not! if
		// not, it starts the service
		initChaskiService();
		
		registerReceivers();

		initGUI();		

	}
	
	private void startTriggeringOfClients(){
		stpeTriggerClients = new ScheduledThreadPoolExecutor(1);
		
		stpeTriggerClients.scheduleAtFixedRate(new Runnable() {
			
			@Override
			public void run() {
				int r = new Random().nextInt();
				
				mChaskiService.triggerIpAddressesOfValidClients("trigger_"+ r);
				
			}
		}, 0, 1000, TimeUnit.MILLISECONDS);
		
	}
	
	private void stopTriggeringOfClients(){
		stpeTriggerClients.shutdownNow();		
	}

	private void initApStates() {
		try {
			mWifiApDisabled = mChaskiService.getWifiApStateDisabled();
			
			mWifiApEnabled = mChaskiService.getWifiApStateEnabled();
			
			apStatesInitialized = true;
			
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void initChaskiService() {

		Intent serviceIntent = new Intent();
		serviceIntent.setClass(this, ChaskiService.class);

		bindService(serviceIntent, chaskiServiceConnection,
				Context.BIND_AUTO_CREATE);

	}

	private void registerReceivers() {
		IntentFilter chaskiFilter = new IntentFilter();
		chaskiFilter.addAction(ChaskiConstants.AP_STATE_ACTION);
		chaskiFilter.addAction(ChaskiConstants.CLIENTS_STATE_ACTION);
		registerReceiver(mBroadcastReceiver, chaskiFilter);
		Log.d(TAG, "Registered broadcast receiver.");
	}

	@Override
	public void onBackPressed() {

		unregisterReceiver(mBroadcastReceiver);
		Log.d(TAG, "Unregistered broadcast receiver.");

		try {
			if (mChaskiService.isApEnabled()) {
				
				stopTriggeringOfClients();
				mChaskiService.disableAp();
			}

		} catch (RemoteException e) {
			e.printStackTrace();
		}

		if (mBoundToChaskiService) {
			unbindService(chaskiServiceConnection);
			mBoundToChaskiService = false;
		}

		super.onBackPressed();
	}

	protected void handleApStateChanged(int currentApState) {

		if (currentApState == mWifiApEnabled) {

			Log.d(TAG, "AP is enabled");

			mTextViewApStatus.setText(getResources().getString(
					R.string.status_enabled));

			try {
				WifiConfiguration wifiConfig = mChaskiService
						.getWifiApConfiguration();

				String ssid = wifiConfig.SSID;
				
				startTriggeringOfClients();
				
				mTextViewApSSID.setText(ssid);

				mTextViewNumberOfClients.setText("0");

				mTextViewIpAddressesOfClients.setText(NOT_APPLICABLE);

				mToggleButtonAp.setChecked(true);
				mToggleButtonAp.setEnabled(true);
				mButtonUpdate.setEnabled(true);
				
				String ip = mChaskiService.getLocalIpAdress();
				Log.d(TAG, "IP is " + ip);

			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		if (currentApState == mWifiApDisabled) {

			Log.d(TAG, "AP is disabled");

			mTextViewApStatus.setText("Disabled");

			mTextViewApSSID.setText("");

			mTextViewNumberOfClients.setText("");

			mTextViewIpAddressesOfClients.setText("");

			mToggleButtonAp.setChecked(false);

			mToggleButtonAp.setEnabled(true);
			// when the AP is disabled, the update button should be disabled too
			mButtonUpdate.setEnabled(false);
		}

	}

	private void initGUI() {

		mToggleButtonAp = (ToggleButton) findViewById(R.id.toggleButton);
		mButtonUpdate = (Button) findViewById(R.id.bUpdate);

		mTextViewApStatus = (TextView) findViewById(R.id.tvApStatus);
		mTextViewApSSID = (TextView) findViewById(R.id.tvApSSID);
		mTextViewNumberOfClients = (TextView) findViewById(R.id.tvNumberofClients);
		mTextViewIpAddressesOfClients = (TextView) findViewById(R.id.tvIPaddressesOfCLients);

		if (mBoundToChaskiService) {
			mToggleButtonAp.setEnabled(true);
		}

		mButtonUpdate.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				// enable access point with another SSID
				String updateRandomStr = generateRandomSuffix();

				final String ssid = PREFIX + updateRandomStr;

				// The current AP will be disabled and a new one using the
				// updated SSID will be deployed
				Log.d(TAG, "Will deploy new hotspot with " + ssid);

				try {
					mChaskiService
							.enableAp(ssid, Util.DEFAULT_NETWORK_PASSWORD);

					// disable as long notification is not received
					mToggleButtonAp.setEnabled(false);
					mButtonUpdate.setEnabled(false);

					mTextViewApStatus.setText(getResources().getString(
							R.string.status_enabling));
					mTextViewApSSID.setText("");
					mTextViewNumberOfClients.setText("");
					mTextViewIpAddressesOfClients.setText("");

				} catch (RemoteException e) {
					e.printStackTrace();
					Log.e(TAG, e.getCause().getMessage());
				}

			}
		});

	}

	public void onToggleClicked(View view) {

		boolean on = ((ToggleButton) view).isChecked();
		if (on) {
			// The toggle is enabled

			if (mWifiManager.isWifiEnabled()) { // listening to the broadcast

				mWifiManager.setWifiEnabled(false);// disabling WiFi
				Log.d(TAG, "WiFi is disabled");

			}

			// enable the AP and
			String mRandomStr = generateRandomSuffix();

			// configure the SSID it with a specific prefix
			final String mApSSID = PREFIX + mRandomStr;

			try {
				mChaskiService.enableAp(mApSSID, Util.DEFAULT_NETWORK_PASSWORD);
				mToggleButtonAp.setEnabled(false);
				mButtonUpdate.setEnabled(false);
				mTextViewApStatus.setText(getResources().getString(
						R.string.status_enabling));

			} catch (RemoteException e) {
				e.printStackTrace();
				Log.e(TAG, e.getCause().getMessage());
			}

		}

		else {
			// Disable the AP

			try {
				stopTriggeringOfClients();
				mChaskiService.disableAp();
				mToggleButtonAp.setEnabled(false);
				mButtonUpdate.setEnabled(false);
				mTextViewApStatus.setText(getResources().getString(
						R.string.status_disabling));
				mTextViewApSSID.setText("");
				mTextViewNumberOfClients.setText("");
				mTextViewIpAddressesOfClients.setText("");

			} catch (RemoteException e) {
				e.printStackTrace();
				Log.e(TAG, e.getCause().getMessage());
			}
		}
	}

	protected String generateRandomSuffix() {
		Random randomGenerator = new Random();
		int randomInt = randomGenerator.nextInt(100);
		String randomStr = Integer.toString(randomInt);

		return randomStr;
	}

}
