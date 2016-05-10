# U2FBLETest 
Test application for U2F BLE device 

This application let you test the compatibility of your BLE stack against a given U2F BLE device, the performance or compliance of said device.

Device detection
=================

Use one of the following detection method for your device

  * Scan, turn on the device, then click Scan again when the device is detected. If everything goes well, it should be usable
  * Name, enter the name of the device, then turn it on. If it can be detected within 5 seconds, it can now be used
  * MAC, enter the address of the device. A connection will only be attempted on the first attempt  

Running a test
===============

Use register first, then auth (P1=0x03) or auth (check) (P1=0x07)

In order to test timeout conditions, clicking one of those option connects to the device if the device is disconnected, and you need to click again to send the commands

The registration signature is only checked if the attestation certificate uses a P-256 public key.

You can also force wrong app parameters or a wrong key handle in the authentication test

Obtaining logs
==============

Logs can be obtained through logcat or by long clicking the log window then copying the logs to the clipboard. 

Contact
=======

Please report bugs and features to hello@ledger.fr

