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

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Vector;

import com.ledger.u2fbletest.utils.BLETransportHelper;
import com.ledger.u2fbletest.utils.Dump;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;

public class U2FBLEDevice {
	
	private enum ScanType {
		SCAN_BY_NAME
	};
	
	public static final UUID U2F_SERVICE_UUID = UUID.fromString("0000FFFD-0000-1000-8000-00805F9B34FB");
	public static final UUID U2F_WRITE_CHARACTERISTIC_UUID = UUID.fromString("F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB");
	public static final UUID U2F_NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB");
	public static final UUID U2F_CONTROLPOINT_LENGTH_CHARACTERISTIC_UUID = UUID.fromString("F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB");
	
	private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	
	private static final int CONNECT_TIMEOUT = 5000;
	private static final int SCAN_TIMEOUT = 5000;
	//private static final int TIMER_READ = 200;
	//private static final int TIMER_DISCOVER = 200;
	private static final int TIMER_READ = 500;
	private static final int TIMER_DISCOVER = 500;
		
	private Logger logger;
	private U2FBLEDeviceNotification notification;
	
	private BluetoothDevice device;
	private String name;
	private String address;
	private int chunkSize;
	private int timeoutMs;
	
	private BluetoothGatt connection;
	private BluetoothGattCharacteristic characteristicWrite;
	private BluetoothGattCharacteristic characteristicNotify;
	private BluetoothGattCharacteristic characteristicControlpointLength;
	
	private Vector<byte[]> fragmentedApduResponse;
	private Vector<byte[]> sendFragments;
	private byte[] fragmentedResponse;
	private boolean pendingWrite;
	private boolean initialized;
	private boolean finalizing;
	private int  state;
	private Context context;
	private Timer timer;
	private TimerTask connectionTimer;
	private boolean connectedOnce;
	private boolean discarded;
		
	private static class LocalScanCallback extends ScanCallback {
		private BluetoothAdapter bluetoothAdapter;
		private Logger logger;
		private U2FBLEDeviceNotification notification;
		private Context context;
		private TimerTask detectTimeout;
		private Timer timer;
		
		public LocalScanCallback(BluetoothAdapter bluetoothAdapter, Logger logger, U2FBLEDeviceNotification notification, Context context) { 
			this.bluetoothAdapter = bluetoothAdapter;
			this.logger = logger;
			this.notification = notification;
			this.context = context;
			timer = new Timer();
			detectTimeout = new TimerTask() {
				@Override
				public void run() {
					LocalScanCallback.this.logger.debug("Timeout detecting device");
					LocalScanCallback.this.notification.onException(null, "Timeout");
					LocalScanCallback.this.bluetoothAdapter.getBluetoothLeScanner().stopScan(LocalScanCallback.this);		
				}				
			};
			timer.schedule(detectTimeout, SCAN_TIMEOUT);									
		}
		
		@Override
		public void onScanFailed(int errorCode) {
			this.notification.onException(null, "Scan failed " + errorCode);			
		}
		
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			logger.debug("Device detected " + result.getDevice().getAddress() + " " + result.getDevice().getName());
			detectTimeout.cancel();
			bluetoothAdapter.getBluetoothLeScanner().stopScan(this);
			U2FBLEDevice device = new U2FBLEDevice(result.getDevice(), this.notification, this.logger, this.context);
			this.notification.onDeviceDetected(device);
		}		
	}
		
	private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
		
		@Override
		public void onCharacteristicChanged(final BluetoothGatt gatt,
				final BluetoothGattCharacteristic characteristic) {
			onCharacteristicChangedInternal(gatt, characteristic);
		}
		
		public void onCharacteristicChangedInternal(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			if (characteristic.equals(characteristicNotify)) {
				byte[] data = characteristic.getValue();
				logger.debug("Notified " + Dump.dump(data));
				switch(BLETransportHelper.getChunkType(data)) {
					case CHUNK_MSG:
					case CHUNK_CONTINUATION:
						break;
					case CHUNK_ERROR:
						connectionTimer.cancel();
						logger.debug("Error reported");
						notification.onException(U2FBLEDevice.this, "Error reported " + data[3]);
						return;
					case CHUNK_KEEPALIVE:
						connectionTimer.cancel();
						createTimer();						
						logger.debug("Keepalive");
						notification.onKeepAlive(U2FBLEDevice.this, data[3]);
						return;
					default:
						connectionTimer.cancel();
						logger.debug("Unexpected data received");
						notification.onException(U2FBLEDevice.this, "Unexpected data received " + Dump.dump(data));
						break;
				}
				fragmentedApduResponse.add(data);
				try {
					fragmentedResponse = BLETransportHelper.join(BLETransportHelper.COMMAND_MSG,  fragmentedApduResponse);
				}
				catch(Exception e) {
					e.printStackTrace();
					notification.onException(U2FBLEDevice.this, "Invalid fragmented response " + e.getMessage());
				}
				if (fragmentedResponse != null) {
					fragmentedApduResponse.removeAllElements();
					logger.debug("Got APDU response " + Dump.dump(fragmentedResponse));					
					if (pendingWrite) {
						logger.debug("Wait for pending write confirmation");
					}
					else {
						connectionTimer.cancel();
						notification.onResponseAvailable(U2FBLEDevice.this, fragmentedResponse);
						fragmentedResponse = null;
					}					
				}				
			}
			else {
				logger.debug("Ignoring characteristic change on " + characteristic.getUuid().toString());
			}			
		}
		
		@Override
		public void onCharacteristicRead(final BluetoothGatt gatt,
				final BluetoothGattCharacteristic characteristic, final int status) {
			onCharacteristicReadInternal(gatt, characteristic, status);
		}
		
		private void enableNotifications() {
			boolean result = connection.setCharacteristicNotification(characteristicNotify, true);
			if (!result) {
				notification.onException(U2FBLEDevice.this, "Failed to enable local notifications");
				return;
			}					
			BluetoothGattDescriptor descriptor = characteristicNotify.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
			descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			result = connection.writeDescriptor(descriptor);
			if (!result) {
				notification.onException(U2FBLEDevice.this, "Failed to enable remote notifications");
				return;
			}					
			createTimer();			
		}
		
		public void onCharacteristicReadInternal(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				byte[] value = characteristic.getValue();
				logger.debug("Read " + Dump.dump(value));
				if (characteristic.equals(characteristicControlpointLength)) {
					connectionTimer.cancel();
					chunkSize = ((value[0] & 0xff) << 8) | (value[1] & 0xff);
					logger.debug("Using chunksize " + chunkSize);
					// Finalize initialization
					enableNotifications();
				}
				else {
					notification.onCharacteristicDataAvailable(U2FBLEDevice.this, value);
				}
			}
			else {
				notification.onException(U2FBLEDevice.this, "Read failed remotely " + status);
			}						
		}
		
		@Override
		public void onCharacteristicWrite(final BluetoothGatt gatt,
				final BluetoothGattCharacteristic characteristic, final int status) {
			onCharacteristicWriteInternal(gatt, characteristic, status);
			
		}
		
		public void onCharacteristicWriteInternal(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (!pendingWrite) {
				logger.debug("Unexpected characteristic write received " + status);
				return;
			}
			if (status == BluetoothGatt.GATT_SUCCESS) {
				logger.debug("Write acknowledged");
				pendingWrite = false;
				if (sendFragments.size() != 0) {
					writeNextFragment();
				}
			}
			else {
				notification.onException(U2FBLEDevice.this, "Write failed remotely " + status);
			}			
		}
		
		@Override
		public void onConnectionStateChange(final BluetoothGatt gatt, final int status,
				final int newState) {
			onConnectionStateChangeInternal(gatt, status, newState);
		}
		
		public void onConnectionStateChangeInternal(BluetoothGatt gatt, int status,
				int newState) {
			if (!finalizing) {
				logger.debug("Connection state " + newState);
				state = newState;
				notification.onConnectionStateChanged(U2FBLEDevice.this, newState);
				if ((newState == BluetoothProfile.STATE_CONNECTED) && initialized) {
					connectionTimer.cancel();
					// or enableNotifications with a timer ...
					createDiscoverTimer();
				}
				else
				if ((newState == BluetoothProfile.STATE_CONNECTED) && !initialized) {
					connectionTimer.cancel();
					// To avoid encryption failure observed on Qualcomm
					createDiscoverTimer();
				}
				/*
				else 
				if ((newState != BluetoothProfile.STATE_CONNECTED) && connectedOnce && !discarded) {
					notification.onException(U2FBLEDevice.this, "Unexpected state " + newState);
				}
				*/
			}			
		}
		
		@Override
		public void onDescriptorRead(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
		}
		
		@Override
		public void onDescriptorWrite(final BluetoothGatt gatt,
				final BluetoothGattDescriptor descriptor, final int status) {
			onDescriptorWriteInternal(gatt, descriptor, status);
		}
		
		public void onDescriptorWriteInternal(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			/*
			if (initialized) {
				logger.debug("Unexpected descriptor write result received");
				return;
			}
			*/
			if (status == BluetoothGatt.GATT_SUCCESS) {
				logger.debug("Descriptor written");
				connectionTimer.cancel();
				initialized = true;
				notification.onInitialized(U2FBLEDevice.this);
			}
			else {
				notification.onException(U2FBLEDevice.this, "Invalid status writing dscriptor " + status);
			}			
		}
		
		@Override
		public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
		}
		
		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
		}
		
		@Override
		public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
		}
		
		@Override
		public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
			onServicesDiscoveredInternal(gatt, status);
		}
		
		private void onServicesDiscoveredInternal(BluetoothGatt gatt, int status) {
			logger.debug("Services discovered");
			connectionTimer.cancel();
			List<BluetoothGattService> services = connection.getServices();
			for (BluetoothGattService service : services) {
				logger.debug("Service : " + service.getUuid());
				if (!service.getUuid().equals(U2F_SERVICE_UUID)) {
					continue;
				}
				List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
				for (BluetoothGattCharacteristic characteristic : characteristics) {
					logger.debug("Characteristic : " + characteristic.getUuid());
					if (characteristic.getUuid().equals(U2F_NOTIFY_CHARACTERISTIC_UUID)) {
						characteristicNotify = characteristic;
					}
					else
					if (characteristic.getUuid().equals(U2F_WRITE_CHARACTERISTIC_UUID)) {
						characteristicWrite = characteristic;
					}
					else
					if (characteristic.getUuid().equals(U2F_CONTROLPOINT_LENGTH_CHARACTERISTIC_UUID)) {
						characteristicControlpointLength = characteristic;
					}
				}
			}		
			if ((characteristicNotify == null) || (characteristicWrite == null) || (characteristicControlpointLength == null))  {
				notification.onException(U2FBLEDevice.this, "Could not find mandatory characteristic or service");
				return;
			}
			// To avoid encryption failure observed on Qualcomm
			createReadTimer();					
		}		
	};
	
	
	public U2FBLEDevice(BluetoothDevice device, U2FBLEDeviceNotification notification, Logger logger, Context context) {
		this.device = device;
		this.notification = notification;
		this.logger = logger;
		this.context = context;
		this.name = device.getName();
		this.address = device.getAddress();
		fragmentedApduResponse = new Vector<byte[]>();
		state = BluetoothProfile.STATE_DISCONNECTED;
		timer = new Timer();
	}
	
	public void updateNotification(U2FBLEDeviceNotification notification) {
		this.notification = notification;
	}
			
	protected static void scanDevice(BluetoothAdapter bluetoothAdapter, U2FBLEDeviceNotification notification, Logger logger, Context context, String data, ScanType scanType) {
		BluetoothLeScanner bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
		ScanFilter.Builder builder = new ScanFilter.Builder();
		switch(scanType) {
			case SCAN_BY_NAME:
				builder.setDeviceName(data);
				break;
		}
		Vector<ScanFilter> filter = new Vector<ScanFilter>();
		filter.add(builder.build());
		ScanSettings.Builder builderScanSettings = new ScanSettings.Builder();
		builderScanSettings.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
		builderScanSettings.setReportDelay(0);
		LocalScanCallback scannerCallback = new LocalScanCallback(bluetoothAdapter, logger, notification, context);
		bluetoothScanner.startScan(filter, builderScanSettings.build(), scannerCallback);		
	}
	
	public static void findByName(BluetoothAdapter bluetoothAdapter, U2FBLEDeviceNotification notification, Logger logger, Context context, String name) {
		scanDevice(bluetoothAdapter, notification, logger, context, name, ScanType.SCAN_BY_NAME);
	}

	public static U2FBLEDevice getByAddress(BluetoothAdapter bluetoothAdapter, U2FBLEDeviceNotification notification, Logger logger, Context context, String address) {
		if (!BluetoothAdapter.checkBluetoothAddress(address)) {
			return null;
		}
		BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
		U2FBLEDevice result = new U2FBLEDevice(device, notification, logger, context);
		return result;
	}
		
	public boolean isConnected() {
		return ((state == BluetoothProfile.STATE_CONNECTED) && !discarded);
	}
	
	public BluetoothDevice getBluetoothDevice() {
		return device;
	}
	
	private void createTimer() {
		connectionTimer = new TimerTask() {
			@Override
			public void run() {
				logger.debug("Connection timeout");
				connection.disconnect();
				notification.onException(U2FBLEDevice.this, "Connection timeout");
			}				
		};
		timer.schedule(connectionTimer, timeoutMs);		
	}		
	
	private void createReadTimer() {
		TimerTask readTimer = new TimerTask() {
			@Override
			public void run() {
				logger.debug("Reading control point length");
				boolean result = connection.readCharacteristic(characteristicControlpointLength);
				if (!result) {
					notification.onException(U2FBLEDevice.this, "Failed to read control point length");
				}
				createTimer();				
			}				
		};
		timer.schedule(readTimer, TIMER_READ);				
	}
	
	private void createDiscoverTimer() {
		TimerTask discoverTimer = new TimerTask() {
			@Override
			public void run() {
				if (!connection.discoverServices()) {
					notification.onException(U2FBLEDevice.this, "Failed to start service discovery");
				}
				else {
					logger.debug("Starting service discovery");
					createTimer();
					connectedOnce = true;
				}				
			}				
		};
		timer.schedule(discoverTimer, TIMER_DISCOVER);						
	}
			
	public void connect(int timeoutMs) {
		if (isConnected()) {
			notification.onConnectionStateChanged(U2FBLEDevice.this, BluetoothProfile.STATE_CONNECTED);
		}
		else {
			this.timeoutMs = timeoutMs;
			if (connection != null) {
				logger.debug("Closing previous GATT connection");
				connection.close();				
			}
			connection = device.connectGatt(context, false, gattCallback);
			createTimer();
		}
	}
	
	public void connect() {
		connect(CONNECT_TIMEOUT);
	}
	
	public void disconnect() {
		discarded = true;
		if ((state == BluetoothProfile.STATE_DISCONNECTED) || (connection == null)) {
			notification.onConnectionStateChanged(U2FBLEDevice.this, BluetoothProfile.STATE_DISCONNECTED);
		}
		else {			
			connection.disconnect();
		}
	}
	
	public String getAddress() {
		return address;
	}
	
	public String getName() {
		return name;
	}
	
	public int getChunkSize() {
		return chunkSize;
	}

	private void writeNextFragment() {
		byte[] fragment = sendFragments.remove(0);
		logger.debug("Writing " + Dump.dump(fragment));
		characteristicWrite.setValue(fragment);
		pendingWrite = true;
		if (!connection.writeCharacteristic(characteristicWrite)) {
			pendingWrite = false;
			notification.onException(U2FBLEDevice.this, "Writing failed locally");
		}					
	}
	
	public boolean exchangeApdu(byte[] apdu) {
		if (!isConnected()) {
			return false;
		}
		createTimer();
		sendFragments = BLETransportHelper.split(BLETransportHelper.COMMAND_MSG, apdu, chunkSize);
		writeNextFragment();
		return true;
	}
}
