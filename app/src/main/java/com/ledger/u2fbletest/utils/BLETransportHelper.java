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

package com.ledger.u2fbletest.utils;

import java.io.ByteArrayOutputStream;
import java.util.Vector;

public class BLETransportHelper {
	
    public static final int COMMAND_PING = 0x81;
    public static final int COMMAND_KEEPALIVE = 0x82;
    public static final int COMMAND_MSG = 0x83;    
    public static final int COMMAND_ERROR = 0xbf;
    
    public static enum ChunkType {
    	CHUNK_PING,
    	CHUNK_KEEPALIVE,
    	CHUNK_MSG,
    	CHUNK_ERROR,
    	CHUNK_UNKNOWN,
    	CHUNK_CONTINUATION,
    }
    
    public static ChunkType getChunkType(byte[] data) {
    	if ((data[0] & 0x80) == 0) {
    		return ChunkType.CHUNK_CONTINUATION;
    	}
    	switch((data[0] & 0xff)) {
    		case COMMAND_PING:
    			return ChunkType.CHUNK_PING;
    		case COMMAND_KEEPALIVE:
    			return ChunkType.CHUNK_KEEPALIVE;
    		case COMMAND_MSG:
    			return ChunkType.CHUNK_MSG;
    		case COMMAND_ERROR:
    			return ChunkType.CHUNK_ERROR;
    		default:
    			return ChunkType.CHUNK_UNKNOWN;
    	}
    }

    public static Vector<byte[]> split(int command, byte[] dataToTransport, int chunksize) {
    	Vector<byte[]> result = new Vector<byte[]>();
    	if (chunksize < 8) {
    		throw new RuntimeException("Invalid chunk size");
    	}
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int remaining_length = dataToTransport.length;
        int offset = 0;
        int seq = 0;
        boolean firstPacket = true;
        
        while (remaining_length > 0) {
        	int l = 0;
        	if (!firstPacket) {
        		baos.write(seq);
        		l = Math.min(chunksize - 1, remaining_length);
        	}
        	else {
        		baos.write(command);
                // first packet has the total transport length
                baos.write(remaining_length >> 8);
                baos.write(remaining_length);
                l = Math.min(chunksize - 3, remaining_length);
        	}        		            
            baos.write(dataToTransport, offset, l);
            remaining_length -= l;
            offset += l;
            result.add(baos.toByteArray());
            baos.reset();
            if (!firstPacket) {
            	seq++;
            }
            firstPacket = false;
        }
        
        return result;            	    	
    }
    
    public static byte[] join(int command, Vector<byte[]> chunks) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int seq = 0;
        int length = -1;
        boolean firstPacket = true;
        for (byte[] chunk : chunks) {
        	if (firstPacket) {
        		if ((int)(chunk[0] & 0xff) != command) {
        			throw new RuntimeException("Unexpected command");
        		}
        		length = ((chunk[1] & 0xff) << 8) | (chunk[2] & 0xff);
        		baos.write(chunk, 3, chunk.length - 3);
        		length -= chunk.length - 3;
        		firstPacket = false;
        	}
        	else {
        		if ((int)(chunk[0] & 0xff) != seq) {
        			throw new RuntimeException("Unexpected sequence");
        		}
        		baos.write(chunk, 1, chunk.length - 1);
        		length -= chunk.length - 1;
        		seq++;
        	}
    		if (length < 0) {
    			throw new RuntimeException("Invalid data length");
    		}        	
        }
        // not all chunks received to be able to concatenate
        if (length != 0) {
            return null;
        }
        // return concatenated data
        return baos.toByteArray();                     
    }
}

