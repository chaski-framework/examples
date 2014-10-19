 /** This class serves to use Client related Chaski Services. The App contains a
  *  button to connect to an AP (with prefix *#) and a button to disconnect from the AP,
  *  it also has a button which you can ask  whether it is connected to a AP at the moment or not **/

package chaski.client.example;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import chaski.api.ChaskiConstants;
import chaski.api.ChaskiService;
import chaski.api.util.Util;

public class ChaskiClientActivity extends Activity {

	private static final String NETWORK_PREFIX = "*#%";

	private static final String TAG = ChaskiClientActivity.class.getName();
			
	private boolean mConnFlag = false; 
	
	private boolean mScanFlag;
	
	// Getting an instance of the WifiManager class
	private WifiManager mWifiManager;
    
    // Getting an instance of the Chaski service
	private ChaskiService   mChaskiService;
	
	private boolean mBoundToChaskiService;
	
	private ScheduledThreadPoolExecutor stpeScanProcess;
	
	private Button mButtonConnect;
	private Button mButtonDisconnect;
	private TextView mTextViewConnectionStatus;
	private TextView mTextViewSSID;
	private TextView mTextViewWifiState;
	private TextView mTextViewIpAddress;
	
	
	final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
              
			 if(intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)){
				
				int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);

				handleWifiStateChanged(wifiState);
			}
			 
			 
			 if(intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)){
				    		
				 if (mConnFlag){
			        handleScanResult();
			     }
			}
			 
			  
			 if(intent.getAction().equals(ChaskiConstants.CONNECTION_STATE_ACTION)){
			    	
			    	int connectionState = intent.getIntExtra(ChaskiConstants.CONNECTION_STATE , ChaskiConstants.CONNECTION_ERROR_STATE );
			    	
			    	String ssid = intent.getStringExtra(ChaskiConstants.KEY_SSID);
			    	
			    	String ipAddress = intent.getStringExtra(ChaskiConstants.KEY_IP_ADDRESS);
			    	
			    	handleConnectionStateChanged(connectionState, ssid, ipAddress);
			    	
			    }
	           
			
		}
		
	};
	
	
	private ServiceConnection conn = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {

			mChaskiService = ((ChaskiService.MyBinder) binder).getService();
			Log.d(TAG, "Chaski service has been bound");
			mBoundToChaskiService = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "Chaski service has been unbound");
			mBoundToChaskiService = false;

		}

	};

	  @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_chaski_client);

        // Getting the WiFi Services
        mWifiManager=(WifiManager)getSystemService(Context.WIFI_SERVICE);
 
        initChaskiService();
					
		registerReceivers();
						
		initGUI(); 
	}




	private void registerReceivers() {
		IntentFilter chaskiFilter = new IntentFilter();
		chaskiFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		chaskiFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		chaskiFilter.addAction(ChaskiConstants.CONNECTION_STATE_ACTION);
		registerReceiver(mBroadcastReceiver, chaskiFilter);	
		
		Log.d(TAG, "Registered broadcast receiver.");
	}
	
	 @Override
	    protected void onStop() {
	        super.onStop();
	        // Unbind from the service
	         if (mBoundToChaskiService) {
	            unbindService(conn);
	            mBoundToChaskiService = false;
	        }
	    }
	   
	 @Override
	public void onBackPressed() {
		
		unregisterReceiver(mBroadcastReceiver);
		Log.d(TAG, "Unregistered broadcast receiver.");
		 
		mWifiManager.setWifiEnabled(false);
		 
		super.onBackPressed();
	}

	 protected void handleWifiStateChanged(int currentWifiState) { 
			
			
			if(currentWifiState==WifiManager.WIFI_STATE_DISABLED){
				
				Log.d(TAG, "WiFi is disabled");
				mTextViewWifiState.setText(getResources().getText(R.string.wifi_disabled));
				
			}
			else if(currentWifiState==WifiManager.WIFI_STATE_ENABLED){
				
				Log.d(TAG, "WiFi is enabled");
				mTextViewWifiState.setText(getResources().getText(R.string.wifi_enabled));
				
				if (mConnFlag){
				startScanningForNetworks();
			    }
			}
			else if(currentWifiState==WifiManager.WIFI_STATE_ENABLING){
				
				Log.d(TAG, "Enabling...");
				mTextViewWifiState.setText(getResources().getText(R.string.wifi_enabling));
				
			}
			else if(currentWifiState==WifiManager.WIFI_STATE_DISABLING){
				Log.d(TAG, "Disabling...");
				mTextViewWifiState.setText(getResources().getText(R.string.wifi_disabling));
				
			}
			else if(currentWifiState==WifiManager.WIFI_STATE_UNKNOWN){
				Log.d(TAG, "Unknown");
				mTextViewWifiState.setText(getResources().getText(R.string.wifi_state_unknonwn));
			}
			
		}

	 private void startScanningForNetworks() {
		 
		 if(!mScanFlag){
			 
			 mScanFlag = true;
			 
			 stpeScanProcess = new ScheduledThreadPoolExecutor(1);
			 
			 Runnable runnable = new Runnable() {
				
				@Override
				public void run() {
					mWifiManager.startScan();
					Log.d(TAG, "Scanning for APs");
				}
			 };		 
			 
			 stpeScanProcess.scheduleAtFixedRate(runnable, 0, 1000, TimeUnit.MILLISECONDS);
	         
	         mTextViewConnectionStatus.setText(getResources().getString(R.string.status_scanning));
		 }
  
	}
	 
	private void stopScanningForNetworks(){
		if(stpeScanProcess != null){
			stpeScanProcess.shutdown();
			mScanFlag=false;
		}
	}
	 

  
	 protected void handleScanResult(){
		 
		 //get scan results
      	 //filter out your AP SSIDs
      	 //connect to one with strongest signal
		 
        List<ScanResult> listOfAPs = mWifiManager.getScanResults();
        
        
        //find the APs with specific prefix (filtering)
  	  
		List <ScanResult> apList = new ArrayList<ScanResult>();
		
        if(listOfAPs!=null){
        	for (ScanResult scanResult : listOfAPs){
      		  
		        String ssid= (scanResult.SSID).toString();
		  
	            if (ssid.startsWith(NETWORK_PREFIX)){
    	        		apList.add(scanResult);	
	    	        }
	  	        	 
	           }
		  
	        }
        
        
        if(apList.size()>0){ 
        	
        	stopScanningForNetworks();
        	
        	//find the strongest signal among aps

	          ScanResult bestSignal = null;
	          
	          for (ScanResult result : apList) {
	            if (bestSignal == null || WifiManager.compareSignalLevel(bestSignal.level, result.level) < 0){
	            		bestSignal = result;
	            }
	           }
	   

	         String apSSIDWithBestSignal = bestSignal.SSID; 

		     	                  
	       // connect to the strongest AP with local cloud prefix   
	         try {
	        	  	
	     	    mChaskiService.connectToAp(apSSIDWithBestSignal, Util.DEFAULT_NETWORK_PASSWORD);
	 	
	 			mTextViewConnectionStatus.setText(getResources().getString(R.string.status_connecting));
	 			
	 	        Log.d(TAG, "Connecting to AP");	       
	 	 					 										
	            } catch (RemoteException e) {
	 	      e.printStackTrace();
	 	      Log.e(TAG,e.getCause().getMessage());
	         }  
	       
	     }
	        
	 } 
        

	 
	 protected void handleConnectionStateChanged(int currentConnectionState, String ssid, String ip) { 
			
		  	
	    	if (currentConnectionState == ChaskiConstants.CONNECTED_STATE){      
				 
	    		Log.d(TAG, "Connected to AP");
	    			    		
	    		if (mConnFlag){
	    		  mConnFlag= false;
	    		}
	    		
	    		mTextViewConnectionStatus.setText(getString(R.string.status_connected));
			    
			    mTextViewSSID.setText(ssid);
			    
			    mTextViewIpAddress.setText(ip);
	    			    		
			    mButtonDisconnect.setEnabled(true);
			    mButtonConnect.setEnabled(false);
	    		
	    	}
	    	
	    
	    	if (currentConnectionState == ChaskiConstants.DISCONNECTED_STATE){
				 
	            Log.d(TAG, "It is disconnected from AP");
	            				
	            if (mConnFlag){
	            	mConnFlag=false;
	            }
	            
			    mTextViewConnectionStatus.setText(getString(R.string.status_disconnected));
			    mTextViewSSID.setText("");
			    mTextViewIpAddress.setText("");
	    		
			    mButtonDisconnect.setEnabled(false);
			    mButtonConnect.setEnabled(true);
				
	    	}
				
		}
	 
			

		private void initGUI() {
		 		
		    	
		    	mButtonConnect =(Button)findViewById(R.id.bConnect);
		    	mButtonDisconnect =(Button)findViewById(R.id.bDisconnect);
		    	
		    	mTextViewConnectionStatus = (TextView)findViewById(R.id.tvConnectionStatus);
		    	mTextViewSSID = (TextView) findViewById(R.id.tvSSID);
		    	mTextViewWifiState = (TextView) findViewById(R.id.tvWiFiState);
		    	
		    	mTextViewIpAddress = (TextView) findViewById(R.id.tvIp);
		    	
		    		    	
		    	mButtonConnect.setOnClickListener(new OnClickListener() { 
		    						
					@Override
					public void onClick(View v) {				
						
						//Wi-Fi is either enabled or disabled
						
						mConnFlag=true; 
						
						mButtonConnect.setEnabled(false);
						
						if(mWifiManager.isWifiEnabled()){  //wifi is enabled
							
							WifiInfo connectionInfo = mWifiManager.getConnectionInfo();
							
							String ssid = connectionInfo.getSSID(); 
							
							//Is device already connected to a network
							  if(ssid==null || ssid.equals("0x")){
								Log.d(TAG, "Wifi enabled but disconnected.");
								
								startScanningForNetworks();
							   }
							  else{   //Wifi enabled and connected to a network
								
								Log.d(TAG, "Wifi enabled and connected to " + ssid);
								
								int netId = connectionInfo.getNetworkId();
								
								boolean result = mWifiManager.removeNetwork(netId); 
								
								if(result){
									Log.d(TAG, ssid + " network has been removed");
									startScanningForNetworks();
								}
							}
							
						}
						else{   //Wifi is disabled
							    //So, Enable Wi-Fi
							
							mWifiManager.setWifiEnabled(true);
							Log.d(TAG, "Enabling wifi ");
						}
						 
					}	 
				});
		    	
		    	
		    	
		    	
		    	mButtonDisconnect.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						// disconnect from the AP with prefix *#Chaski
					
							 mChaskiService.disconnectFromAp();
							 Log.d(TAG, "Disconnecting from AP... ");
							 mTextViewConnectionStatus.setText(getResources().getString(R.string.status_disconnecting));
							 mButtonDisconnect.setEnabled(false);
					}
				});		    	
		    	
			}




		private void initChaskiService() {
			 
			 	Intent serviceIntent=new Intent(); 
				serviceIntent.setClass(this, ChaskiService.class);
				
				bindService(serviceIntent, conn, Context.BIND_AUTO_CREATE);
			
		}

}
