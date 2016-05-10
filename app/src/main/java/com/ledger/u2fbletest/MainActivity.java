/*
*******************************************************************************    
*   U2F BLE Tester
*   (c) 2016 Ledger
*   
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*   limitations under the License.
********************************************************************************/

package com.ledger.u2fbletest;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;
import com.ledger.u2fbletest.apdus.Authenticate;
import com.ledger.u2fbletest.apdus.AuthenticateResponse;
import com.ledger.u2fbletest.apdus.Register;
import com.ledger.u2fbletest.apdus.RegisterResponse;
import com.ledger.u2fbletest.crypto.U2FCrypto;
import com.ledger.u2fbletest.utils.Dump;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements Logger {
	
	private static final int REQUEST_ENABLE_BT = 0;
	private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
	private static final byte P256_SUBJECT_PUBLIC_KEY_MARKER[] = { (byte)0x30, (byte)0x59, (byte)0x30, (byte)0x13, (byte)0x06, (byte)0x07, (byte)0x2A, (byte)0x86, (byte)0x48, (byte)0xCE, (byte)0x3D, (byte)0x02, (byte)0x01, (byte)0x06, (byte)0x08, (byte)0x2A, (byte)0x86, (byte)0x48, (byte)0xCE, (byte)0x3D, (byte)0x03, (byte)0x01, (byte)0x07, (byte)0x03, (byte)0x42, (byte)0x00 };
	
	public static final String TAG = "U2FBLETest";
	public static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
				
	private TextView logView;
	private Button scanButton;
	private Button getByNameButton;
	private Button getByAddressButton;
	private Button clearLogsButton;
	private Button registerButton;
	private Button authenticateButton;
	private Button authenticateCheckButton;
	private Button invalidApButton;
	private Button invalidKeyHandleButton;
	private boolean scanning;
	private boolean invalidAp;
	private boolean invalidKeyHandle;
		
	private boolean bluetoothInitialized;
	
	private BluetoothManager bluetoothManager;
	private BluetoothAdapter bluetoothAdapter;
	
	private HashMap<String, BluetoothDevice> devices;
	
	private U2FBLEDevice targetDevice;
	private RegisterResponse registerResponse;
	private AuthenticateResponse authenticateResponse;
	
	public class AuthenticateNotification implements U2FBLEDeviceNotification {
		
		private Logger logger;
		private boolean checkOnly;
		
		public AuthenticateNotification(Logger logger, boolean checkOnly) {
			this.logger = logger;
			this.checkOnly = checkOnly;
			targetDevice.updateNotification(this);			
		}
		
		private Authenticate getAuthenticate() {
			byte challenge[] = new byte[32];
			byte applicationParameters[] = new byte[32];
			byte[] originalKeyHandle = registerResponse.getKeyHandle();
			byte[] keyHandle;
			for(byte i=0; i<32; i++) {
				challenge[i] = (byte)(i | 0x10);
				applicationParameters[i] = (byte)(i | 0x80);				
			}
			if (invalidAp) {
				applicationParameters[0] = (byte)0xff;
			}
			if (invalidKeyHandle) {
				keyHandle = new byte[originalKeyHandle.length];
				System.arraycopy(originalKeyHandle, 0, keyHandle, 0, keyHandle.length);
				keyHandle[0] = (byte)0xff;
				keyHandle[1] = (byte)0xff;
				keyHandle[keyHandle.length - 1] = (byte)0xff;
			}
			else {
				keyHandle = originalKeyHandle;
			}
			return new Authenticate(challenge, applicationParameters, keyHandle, checkOnly);
		}
		
		private void sendAuthenticate() {
			targetDevice.exchangeApdu(getAuthenticate().serialize());						
		}
		
		public void start() {
			if (!targetDevice.isConnected()) {
				logger.debug("Connecting device");
				targetDevice.connect();
			}
			else {
				sendAuthenticate();
			}
		}

		@Override
		public void onDeviceDetected(U2FBLEDevice device) {
		}

		@Override
		public void onInitialized(U2FBLEDevice device) {
			logger.debug("Device connected, click again to send");
			//sendAuthenticate();
		}

		@Override
		public void onConnectionStateChanged(U2FBLEDevice device, int state) {
			logger.debug(getDeviceCommonName(device) + " changed state " + state);			
		}

		@Override
		public void onResponseAvailable(U2FBLEDevice device, byte[] response) {
			logger.debug(getDeviceCommonName(device) + " response " + Dump.dump(response));
			authenticateResponse = AuthenticateResponse.parse(response);
			logger.debug(getDeviceCommonName(device) + " authenticate response " + authenticateResponse);
			Authenticate authenticate = getAuthenticate();
			logger.debug("Signature verified " + U2FCrypto.checkAuthenticateSignature(authenticate, authenticateResponse, registerResponse));
		}

		@Override
		public void onKeepAlive(U2FBLEDevice device, int reason) {
			logger.debug(getDeviceCommonName(device) + " keepalive " + reason);
			
		}

		@Override
		public void onCharacteristicDataAvailable(U2FBLEDevice device, byte[] response) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onException(U2FBLEDevice device, String reason) {
			logger.debug(getDeviceCommonName(device) + " exception reported : " + reason);			
		}		
	}
		
	public class RegisterNotification implements U2FBLEDeviceNotification {
		
		private Logger logger;
		
		public RegisterNotification(Logger logger) {
			this.logger = logger;
			targetDevice.updateNotification(this);
		}
		
		private Register getRegister() {
			byte challenge[] = new byte[32];
			byte applicationParameters[] = new byte[32];
			for(byte i=0; i<32; i++) {
				challenge[i] = i;
				applicationParameters[i] = (byte)(i | 0x80);
			}
			return new Register(challenge, applicationParameters);
		}
		
		public void sendRegister() {
			targetDevice.exchangeApdu(getRegister().serialize());						
		}
		
		public void start() {
			if (!targetDevice.isConnected()) {
				logger.debug("Connecting device");
				targetDevice.connect();
			}
			else {
				sendRegister();
			}			
		}

		@Override
		public void onDeviceDetected(U2FBLEDevice device) {
		}

		@Override
		public void onInitialized(U2FBLEDevice device) {
			logger.debug("Device connected, click again to send");
			//sendRegister();
		}

		@Override
		public void onConnectionStateChanged(U2FBLEDevice device, int state) {
			logger.debug(getDeviceCommonName(device) + " changed state " + state);			
		}

		@Override
		public void onResponseAvailable(U2FBLEDevice device, byte[] response) {
			logger.debug(getDeviceCommonName(device) + " response " + Dump.dump(response));
			registerResponse = RegisterResponse.parse(response);
			logger.debug(getDeviceCommonName(device) + " register response " + registerResponse);
			try {
				X509Certificate certificate = (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(registerResponse.getCertificate()));
				byte[] encodedPublicKey = certificate.getPublicKey().getEncoded();
				if (Arrays.equals(Arrays.copyOfRange(encodedPublicKey, 0, P256_SUBJECT_PUBLIC_KEY_MARKER.length), P256_SUBJECT_PUBLIC_KEY_MARKER)) {
					byte[] publicKey = Arrays.copyOfRange(encodedPublicKey, P256_SUBJECT_PUBLIC_KEY_MARKER.length, P256_SUBJECT_PUBLIC_KEY_MARKER.length + 65);
					Register register = getRegister();
					logger.debug("Signature verified " + U2FCrypto.checkRegisterSignature(register, registerResponse, publicKey));
				}
				else {
					logger.debug("Cannot verify signature - certificate not handled");
				}
			}
			catch(Exception e) {
				e.printStackTrace();
				logger.debug("Error decoding certificate");
			}
			
		}

		@Override
		public void onKeepAlive(U2FBLEDevice device, int reason) {
			logger.debug(getDeviceCommonName(device) + " keepalive " + reason);
			
		}

		@Override
		public void onCharacteristicDataAvailable(U2FBLEDevice device, byte[] response) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onException(U2FBLEDevice device, String reason) {
			logger.debug(getDeviceCommonName(device) + " exception reported : " + reason);			
		}		
	}

	public class ScanNotification implements U2FBLEDeviceNotification {			
		
		private Vector<BluetoothDevice> sourceDevices;
		private Vector<U2FBLEDevice> targetDevices;
		private U2FBLEDevice previousDevice;
		private Logger logger;
		
		public ScanNotification(Vector<BluetoothDevice> devices, Logger logger) {
			this.sourceDevices = devices;
			this.logger = logger;
			targetDevices = new Vector<U2FBLEDevice>();
		}
		
		public void start() {
			moveNext();
		}
		
		private void moveNext() {
			if (previousDevice != null) {
				previousDevice.disconnect();
			}
			if (sourceDevices.size() != 0) {
				BluetoothDevice testDevice = sourceDevices.remove(0);
				logger.debug("Test candidate " + testDevice.getAddress() + " " + (testDevice.getName() != null ? testDevice.getName() : ""));
				U2FBLEDevice candidate = new U2FBLEDevice(testDevice, this, MainActivity.this, MainActivity.this);
				previousDevice = candidate;
				candidate.connect();
			}
			else {
				logger.debug("Test finished - " + targetDevices.size() + " devices found");
			}
		}

		@Override
		public void onDeviceDetected(U2FBLEDevice device) {			
		}
				
		@Override
		public void onInitialized(U2FBLEDevice device) {
			logger.debug(getDeviceCommonName(device) + " is a U2F authenticator");
			previousDevice = null;
			targetDevices.add(device);
			targetDevice = device;
			moveNext();
		}

		@Override
		public void onConnectionStateChanged(U2FBLEDevice device, int state) {
			logger.debug(getDeviceCommonName(device) + " changed state " + state);
		}

		@Override
		public void onResponseAvailable(U2FBLEDevice device, byte[] response) {
		}

		@Override
		public void onKeepAlive(U2FBLEDevice device, int reason) {
			logger.debug(getDeviceCommonName(device) + " keepalive " + reason);			
		}		
		
		@Override
		public void onCharacteristicDataAvailable(U2FBLEDevice device, byte[] response) {			
		}

		@Override
		public void onException(U2FBLEDevice device, String reason) {
			logger.debug(getDeviceCommonName(device) + " is not a U2F authenticator : " + reason);
			moveNext();
		}
		
	}	

	public class GetSingleDeviceNotification implements U2FBLEDeviceNotification {			
		
		private Logger logger;
		
		public GetSingleDeviceNotification(Logger logger) {
			this.logger = logger;
		}
				
		@Override
		public void onDeviceDetected(U2FBLEDevice device) {			
			logger.debug(getDeviceCommonName(device) + " reported");
			targetDevice = device;
		}
				
		@Override
		public void onInitialized(U2FBLEDevice device) {
		}

		@Override
		public void onConnectionStateChanged(U2FBLEDevice device, int state) {
			logger.debug(getDeviceCommonName(device) + " changed state " + state);
		}

		@Override
		public void onResponseAvailable(U2FBLEDevice device, byte[] response) {
		}

		@Override
		public void onKeepAlive(U2FBLEDevice device, int reason) {
			logger.debug(getDeviceCommonName(device) + " keepalive " + reason);			
		}		
		
		@Override
		public void onCharacteristicDataAvailable(U2FBLEDevice device, byte[] response) {			
		}

		@Override
		public void onException(U2FBLEDevice device, String reason) {
			logger.debug(getDeviceCommonName(device) + " reported exception : " + reason);
		}
		
	}		
	
	private final ScanCallback leScanCallback = new ScanCallback() {
		@Override
		public void onScanFailed(int errorCode) {
			super.onScanFailed(errorCode);
		}
		
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			super.onScanResult(callbackType, result);
			if (devices != null) {
				BluetoothDevice device = result.getDevice();				
				/*
				if ((device.getName() != null) && !devices.containsKey(device.getAddress())) {
					debug("New device detected " + device.getAddress() + " " + device.getName());
					if (device.getName().toUpperCase().contains("U2F")) {					
						devices.put(device.getAddress(), device);
					}
				}
				*/
				if (!devices.containsKey(device.getAddress())) {
					debug("New device detected " + device.getAddress() + "/" + (device.getName() != null ? device.getName() : ""));					
					devices.put(device.getAddress(), device);
				}				
			}
		}
	};
	
	private void commonLog(final String logType, final String message) {	
		Log.d(TAG, message);
		runOnUiThread(new Runnable() {
			public void run() {
				Date currentDate = new Date();
				logView.append(dateFormat.format(currentDate) + " " + message);
				logView.append("\r\n");
			}
		});
	}
	
	@Override
	public void info(String message) {
		commonLog("info", message);
		
	}

	@Override
	public void error(String message) {
		commonLog("error", message);		
	}

	@Override
	public void debug(String message) {
		commonLog("debug", message);		
	}	 
		
	private boolean initializeBluetooth() {
		if (bluetoothManager == null) {
			bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (bluetoothManager == null) {
				Log.d(TAG, "Failed to initialize BluetoothManager");
				Toast.makeText(MainActivity.this, "Failed to initialize BluetoothManager", Toast.LENGTH_LONG).show();
				return false;
			}
		}
		if (bluetoothAdapter == null) {
			bluetoothAdapter = bluetoothManager.getAdapter();
			if (bluetoothAdapter == null) {
				Log.d(TAG, "Failed to get BluetoothAdapter");
				Toast.makeText(MainActivity.this, "Failed to get BluetoothAdapter", Toast.LENGTH_LONG).show();
				return false;
			}
		}
		if (!bluetoothAdapter.isEnabled()) {
			Log.d(TAG, "Bluetooth is not enabled, retry");
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			return false;
		}
		return true;		
	}
	
	private void startScan() {
		BluetoothLeScanner bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
		ScanFilter.Builder builder = new ScanFilter.Builder();		
		Vector<ScanFilter> filter = new Vector<ScanFilter>();
		filter.add(builder.build());
		ScanSettings.Builder builderScanSettings = new ScanSettings.Builder();		
		builderScanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
		builderScanSettings.setReportDelay(0);
		bluetoothScanner.startScan(filter, builderScanSettings.build(), leScanCallback);
	}
	
	private String getDeviceCommonName(U2FBLEDevice device) {
		if (device == null || ((device.getAddress() == null) && (device.getName() == null))) {
			return "[no device]";
		}
		if (device.getName() == null) {
			return "[noname/]" + device.getAddress();
		}
		return "[" + device.getName() + "/" + device.getAddress() + "]";
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		logView = (TextView)findViewById(R.id.logView);
		logView.setMovementMethod(new ScrollingMovementMethod());
		logView.setTextIsSelectable(true);
		registerForContextMenu(logView);
		scanButton = (Button)findViewById(R.id.scanButton);
		getByNameButton = (Button)findViewById(R.id.nameButton);
		getByAddressButton = (Button)findViewById(R.id.addressButton);
		clearLogsButton = (Button)findViewById(R.id.clearLogButton);
		registerButton = (Button)findViewById(R.id.registerButton);
		authenticateButton = (Button)findViewById(R.id.authenticateButton);
		authenticateCheckButton = (Button)findViewById(R.id.authenticateCheckButton);
		invalidApButton = (Button)findViewById(R.id.invalidAp);
		invalidKeyHandleButton = (Button)findViewById(R.id.invalidKeyhandle);
		scanButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				 if (!bluetoothInitialized) {
					 bluetoothInitialized = initializeBluetooth();
					 if (!bluetoothInitialized) {
						 return;
					 }
				 }		
				 if (!scanning) {
					 debug("Start scan");
					 scanning = true;
					 devices = new HashMap<String, BluetoothDevice>();
					 startScan();
				 }
				 else {
					 debug("Stop scan");
					 scanning = false;
					 BluetoothLeScanner bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
					 bluetoothScanner.stopScan(leScanCallback);
					 Vector<BluetoothDevice> scannedDevices = new Vector<BluetoothDevice>();
					 scannedDevices.addAll(devices.values());
					 ScanNotification scanNotification = new ScanNotification(scannedDevices, MainActivity.this);
					 scanNotification.start();
				 }
			}			
		});		
		getByNameButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				 if (!bluetoothInitialized) {
					 bluetoothInitialized = initializeBluetooth();
					 if (!bluetoothInitialized) {
						 return;
					 }
				 }						
				LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
				View dialogView = inflater.inflate(R.layout.dialog_device, null);
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				builder.setView(dialogView);
				final EditText userInput = (EditText)dialogView.findViewById(R.id.dialogInput);
				TextView dialogTitle = (TextView)dialogView.findViewById(R.id.dialogPrompt);
				dialogTitle.setText("Enter the device name");
				builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String name = userInput.getText().toString();
						debug("Scanning for " + name);
						U2FBLEDevice.findByName(bluetoothAdapter, new GetSingleDeviceNotification(MainActivity.this), MainActivity.this, MainActivity.this, name);
					}
				});
				builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				});
				builder.setCancelable(false);
				builder.show();				
			}
		});
		getByAddressButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				 if (!bluetoothInitialized) {
					 bluetoothInitialized = initializeBluetooth();
					 if (!bluetoothInitialized) {
						 return;
					 }
				 }						
				LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
				View dialogView = inflater.inflate(R.layout.dialog_device, null);
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				builder.setView(dialogView);
				final EditText userInput = (EditText)dialogView.findViewById(R.id.dialogInput);
				TextView dialogTitle = (TextView)dialogView.findViewById(R.id.dialogPrompt);
				dialogTitle.setText("Enter the device address");
				builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String address = userInput.getText().toString();
						if (BluetoothAdapter.checkBluetoothAddress(address)) {
							debug("Using device address " + address);
							targetDevice = U2FBLEDevice.getByAddress(bluetoothAdapter, new GetSingleDeviceNotification(MainActivity.this), MainActivity.this, MainActivity.this, address);
						}
						else {
							debug("Invalid device address");
						}
					}
				});
				builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				});
				builder.setCancelable(false);
				builder.show();				
			}
		});				
		registerButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (targetDevice == null) {
					debug("No device selected");
				}
				else {
					 RegisterNotification registerNotification = new RegisterNotification(MainActivity.this);
					 registerNotification.start();					
				}
			}
		});
		authenticateButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (targetDevice == null) {
					debug("No device selected");
				}
				else
				if (registerResponse == null) {
					debug("No register response");
				}
				else {
					 AuthenticateNotification authenticateNotification = new AuthenticateNotification(MainActivity.this, false);
					 authenticateNotification.start();					
				}
			}
		});
		authenticateCheckButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (targetDevice == null) {
					debug("No device selected");
				}
				else
				if (registerResponse == null) {
					debug("No register response");
				}
				else {
					 AuthenticateNotification authenticateNotification = new AuthenticateNotification(MainActivity.this, true);
					 authenticateNotification.start();					
				}
			}
		});				
		clearLogsButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				logView.setText("");
			}			
		});
		invalidApButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				invalidAp = !invalidAp;
				if (invalidAp) {
					debug("Using invalid application parameters");
				}
				else {
					debug("Using regular application parameters");
				}
			}
		});
		invalidKeyHandleButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				invalidKeyHandle = !invalidKeyHandle;
				if (invalidKeyHandle) {
					debug("Using invalid key handle");
				}
				else {
					debug("Using regular key handle");
				}
			}
		});		
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("This app needs location access");
				builder.setMessage("Please grant location access so this app can detect beacons");
				builder.setPositiveButton(android.R.string.ok, null);
				builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
					
					@Override
					public void onDismiss(DialogInterface arg0) {
						requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, PERMISSION_REQUEST_COARSE_LOCATION);
						
					}
				});
				builder.show();
			}
		}
	}
		
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
	    menu.add(0, v.getId(), 0, "Copy to clipboard");
	    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
	    clipboard.setPrimaryClip(ClipData.newPlainText(TAG, logView.getText()));
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch(requestCode) {
			case PERMISSION_REQUEST_COARSE_LOCATION:
				if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					Log.d(TAG, "Coarse location permission granted");
				}
				else {
					final AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle("Functionality limited");
					builder.setMessage("Since location access has not been granted, this app will only be able to fetch devices by address");
					builder.setPositiveButton(android.R.string.ok, null);
					builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
						
						@Override
						public void onDismiss(DialogInterface dialog) {
							// TODO Auto-generated method stub
							
						}
					});
					builder.show();
				}
		}
	}
}
