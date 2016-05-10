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

public interface U2FBLEDeviceNotification {
	
	public void onDeviceDetected(U2FBLEDevice device);
	public void onInitialized(U2FBLEDevice device);
	public void onConnectionStateChanged(U2FBLEDevice device, int state);
	public void onResponseAvailable(U2FBLEDevice device, byte[] response);
	public void onKeepAlive(U2FBLEDevice device, int reason);
	public void onCharacteristicDataAvailable(U2FBLEDevice device, byte[] response);
	public void onException(U2FBLEDevice device, String reason);	

}
