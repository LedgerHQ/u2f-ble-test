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

package com.ledger.u2fbletest.crypto;

import java.io.IOException;

import org.spongycastle.asn1.ASN1InputStream;
import org.spongycastle.asn1.ASN1Integer;
import org.spongycastle.asn1.ASN1Sequence;
import org.spongycastle.asn1.x9.X9ECParameters;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.ec.CustomNamedCurves;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.signers.ECDSASigner;

import com.ledger.u2fbletest.apdus.Authenticate;
import com.ledger.u2fbletest.apdus.AuthenticateResponse;
import com.ledger.u2fbletest.apdus.Register;
import com.ledger.u2fbletest.apdus.RegisterResponse;

public class U2FCrypto {
	
	public static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256r1");
	public static final ECDomainParameters CURVE = new ECDomainParameters(CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());
	
	public static boolean checkRegisterSignature(Register input, RegisterResponse output, byte[] publicKeyPoint) {
		ECDSASigner signer = new ECDSASigner();
		SHA256Digest sha256 = new SHA256Digest();
		byte[] message = new byte[32];
		ECPublicKeyParameters parameters = new ECPublicKeyParameters(CURVE.getCurve().decodePoint(publicKeyPoint), CURVE);
		signer.init(false, parameters);
		sha256.update((byte)0x00);
		sha256.update(input.getApplicationParameter(), 0, input.getApplicationParameter().length);
		sha256.update(input.getChallenge(), 0, input.getChallenge().length);
		sha256.update(output.getKeyHandle(), 0, output.getKeyHandle().length);
		sha256.update(output.getPublicKey(), 0, output.getPublicKey().length);
		sha256.doFinal(message, 0);
		ASN1InputStream decoder = new ASN1InputStream(output.getSignature());
		try {
			ASN1Sequence seq = ASN1Sequence.getInstance(decoder.readObject());
			ASN1Integer r = (ASN1Integer)seq.getObjectAt(0);
			ASN1Integer s = (ASN1Integer)seq.getObjectAt(1);
			return signer.verifySignature(message, r.getValue(), s.getValue());
		}
		catch(IOException e) {
			return false;
		}
		finally {
			try {
				decoder.close();
			}
			catch(IOException e) {				
			}
		}		
	}
	
	public static boolean checkAuthenticateSignature(Authenticate input, AuthenticateResponse output, RegisterResponse registerData) {
		ECDSASigner signer = new ECDSASigner();
		SHA256Digest sha256 = new SHA256Digest();
		byte[] message = new byte[32];
		ECPublicKeyParameters parameters = new ECPublicKeyParameters(CURVE.getCurve().decodePoint(registerData.getPublicKey()), CURVE);
		signer.init(false, parameters);
		sha256.update(input.getApplicationParameter(), 0, input.getApplicationParameter().length);
		sha256.update(output.getUserPresenceFlag());
		sha256.update((byte)((output.getCounter() >> 24) & 0xff));
		sha256.update((byte)((output.getCounter() >> 16) & 0xff));
		sha256.update((byte)((output.getCounter() >> 8) & 0xff));
		sha256.update((byte)(output.getCounter() & 0xff));
		sha256.update(input.getChallenge(), 0, input.getChallenge().length);
		sha256.doFinal(message, 0);
		ASN1InputStream decoder = new ASN1InputStream(output.getSignature());
		try {
			ASN1Sequence seq = ASN1Sequence.getInstance(decoder.readObject());
			ASN1Integer r = (ASN1Integer)seq.getObjectAt(0);
			ASN1Integer s = (ASN1Integer)seq.getObjectAt(1);
			return signer.verifySignature(message, r.getValue(), s.getValue());
		}
		catch(IOException e) {
			return false;
		}			
		finally {
			try {
				decoder.close();
			}
			catch(IOException e) {				
			}
		}
	}
	
}
