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

package com.ledger.u2fbletest.apdus;

import java.io.ByteArrayOutputStream;

import com.ledger.u2fbletest.utils.Dump;

public class Authenticate {
	
	private byte[] challenge;
	private byte[] applicationParameter;
	private byte[] keyHandle;
	private boolean checkOnly;
	
	public Authenticate(byte[] challenge, byte[] applicationParameter, byte[] keyHandle, boolean checkOnly) {
		if (challenge.length != 32) {
			throw new RuntimeException("Invalid challenge");
		}
		if (applicationParameter.length != 32) {
			throw new RuntimeException("Invalid application parameter");
		}
		this.challenge = challenge;
		this.applicationParameter = applicationParameter;
		this.keyHandle = keyHandle;
		this.checkOnly = checkOnly;
	}
	
	public Authenticate(byte[] challenge, byte[] applicationParameter, byte[] keyHandle) {
		this(challenge, applicationParameter, keyHandle, false);
	}
	
	public byte[] getChallenge() {
		return challenge;
	}
	
	public byte[] getApplicationParameter() {
		return applicationParameter;
	}
	
	public byte[] getKeyHandle() {
		return keyHandle;
	}
	
	public boolean isCheckOnly() {
		return checkOnly;
	}
	
	public byte[] serialize() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write(0x00); // cla
		bos.write(0x02); // ins
		bos.write(checkOnly ? 0x07 : 0x03); // p1
		bos.write(0x00); // p2
		bos.write(0x00); // 00
		bos.write(0x00); // l1
		bos.write(0x40 + 1 + keyHandle.length); // l2
		bos.write(challenge, 0, 32);
		bos.write(applicationParameter, 0, 32);
		bos.write(keyHandle.length);
		bos.write(keyHandle, 0, keyHandle.length);
		bos.write(0x00); // le1
		bos.write(0x00); // le2
		return bos.toByteArray();
	}
	
	public String toString() {
		StringBuffer response = new StringBuffer();
		response.append("Authenticate");
		response.append("\n\tchallenge : ").append(Dump.dump(challenge));
		response.append("\n\tapplication parameter : ").append(Dump.dump(applicationParameter));
		response.append("\n\tkey handle : ").append(Dump.dump(keyHandle));
		response.append("\n\tcheck only : ").append(checkOnly);
		response.append("\n");
		return response.toString();
	}	

}
