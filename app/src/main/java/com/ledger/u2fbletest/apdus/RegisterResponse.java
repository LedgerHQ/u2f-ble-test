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

public class RegisterResponse {
	
	private static final byte RESERVED = 0x05;
	private static final byte DER_SEQ = 0x30;
	private static final byte DER_LEN_1 = (byte)0x81;
	private static final byte DER_LEN_2 = (byte)0x82;
	
	private byte[] publicKey;
	private byte[] keyHandle;
	private byte[] certificate;
	private byte[] signature;
	
	public RegisterResponse(byte[] publicKey, byte[] keyHandle, byte[] certificate, byte[] signature) {
		this.publicKey = publicKey;
		this.keyHandle = keyHandle;
		this.certificate = certificate;
		this.signature = signature;
	}
	
	public static RegisterResponse parse(byte[] data) {
		int offset = 0;		
		if (data[offset++] != RESERVED) {
			throw new RuntimeException("Invalid reserved byte");
		}
		byte[] publicKey = new byte[65];
		System.arraycopy(data, offset, publicKey, 0, 65);
		offset += 65;
		int keyHandleLength = (data[offset++] & 0xff);
		byte[] keyHandle = new byte[keyHandleLength];
		System.arraycopy(data,  offset, keyHandle, 0, keyHandleLength);
		offset += keyHandleLength;		
		if (data[offset] != DER_SEQ) {
			throw new RuntimeException("Invalid DER sequence for certificate");
		}
		int certificateLength;
		int certificateHeaderLength;		
		if (data[offset + 1] == DER_LEN_1) {
			certificateLength = (data[offset + 2] & 0xff);
			certificateHeaderLength = 3;
		}
		else
		if (data[offset + 1] == DER_LEN_2) {
			certificateLength = ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
			certificateHeaderLength = 4;
		}
		else {
			throw new RuntimeException("Invalid certificate length");
		}
		byte[] certificate = new byte[certificateHeaderLength + certificateLength];
		System.arraycopy(data,  offset, certificate, 0, certificateHeaderLength + certificateLength);
		offset += certificateHeaderLength + certificateLength;
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
		return new RegisterResponse(publicKey, keyHandle, certificate, signature);
	}
	
	public byte[] serialize() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write(RESERVED);
		bos.write(publicKey, 0, publicKey.length);
		bos.write(keyHandle.length);
		bos.write(keyHandle, 0, keyHandle.length);
		bos.write(certificate, 0, certificate.length);
		bos.write(signature, 0, signature.length);
		return bos.toByteArray();
	}
	
	public byte[] getPublicKey() {
		return publicKey;
	}
	
	public byte[] getKeyHandle() {
		return keyHandle;
	}
	
	public byte[] getCertificate() {
		return certificate;
	}
	
	public byte[] getSignature() {
		return signature;
	}
	
	public String toString() {
		StringBuffer response = new StringBuffer();
		response.append("Register response");
		response.append("\n\tPublic key : ").append(Dump.dump(publicKey));
		response.append("\n\tKey handle : ").append(Dump.dump(keyHandle));
		response.append("\n\tCertificate : ").append(Dump.dump(certificate));
		response.append("\n\tSignature : ").append(Dump.dump(signature));
		return response.toString();
	}

}
