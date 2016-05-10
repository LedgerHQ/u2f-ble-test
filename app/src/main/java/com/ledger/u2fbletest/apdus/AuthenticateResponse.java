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

public class AuthenticateResponse {
	
	private static final byte DER_SEQ = 0x30;

	private byte userPresenceFlag;
	private int counter;
	private byte[] signature;

	public AuthenticateResponse(byte userPresenceFlag, int counter, byte[] signature) {
		this.userPresenceFlag = userPresenceFlag;
		this.counter = counter;
		this.signature = signature;
	}
	
	public static AuthenticateResponse parse(byte[] data) {
		int offset = 0;
		byte userPresenceFlag = data[offset++];
		int counter = ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16) | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
		offset += 4;
		if (data[offset] != DER_SEQ) {
			throw new RuntimeException("Invalid DER sequence for signature");
		}
		int signatureLength;
		signatureLength = (data[offset + 1] & 0xff);
		byte[] signature = new byte[signatureLength + 2];
		System.arraycopy(data, offset, signature, 0, signatureLength + 2);
		offset += signatureLength + 2;
		/*
		if (offset != data.length) {
			throw new RuntimeException("Unexpected extra data");
		}
		*/
		return new AuthenticateResponse(userPresenceFlag, counter, signature);
	}
	
	public byte[] serialize() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write(userPresenceFlag);
		bos.write((counter >> 24) & 0xff);
		bos.write((counter >> 16) & 0xff);
		bos.write((counter >> 8) & 0xff);
		bos.write(counter & 0xff);
		bos.write(signature, 0, signature.length);
		return bos.toByteArray();
	}
	
	public byte getUserPresenceFlag() {
		return userPresenceFlag;
	}
	
	public int getCounter() {
		return counter;
	}
	
	public byte[] getSignature() {
		return signature;
	}
	
	public String toString() {
		StringBuffer response = new StringBuffer();
		response.append("Authenticate response");
		response.append("\n\tUser presence : ").append(userPresenceFlag);
		response.append("\n\tCounter : ").append(counter);
		response.append("\n\tSignature : ").append(Dump.dump(signature));
		return response.toString();
	}
	
}
