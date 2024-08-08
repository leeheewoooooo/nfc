package com.example.tel

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class UserActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFilters: Array<IntentFilter>
    private lateinit var phoneNumber: String
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var databaseReference: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseReference = firebaseDatabase.getReference();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val getIntent = getIntent()
        phoneNumber = getIntent.getStringExtra("PHONE_NUMBER") ?: ""

        // Create an Intent to handle the NFC data
        val intent = Intent(this, javaClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        // Create an IntentFilter for NFC tag discovery
        val tagDetected = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        intentFilters = arrayOf(tagDetected)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            tag?.let {
                writePhoneNumberToTag(it)
            }
        }
    }

    private fun writePhoneNumberToTag(tag: Tag) {
        val ndef = Ndef.get(tag) ?: run {
            Toast.makeText(this, "NDEF is not supported on this tag.", Toast.LENGTH_SHORT).show()
            databaseReference.child("phoneNumber").push().setValue(phoneNumber)
            return
        }
        val message = NdefMessage(
            arrayOf(
                NdefRecord.createTextRecord("en", phoneNumber)  // Creating a text record with the phone number
            )
        )
        try {
            ndef.connect()
            if (!ndef.isWritable) {
                Toast.makeText(this, "Tag is not writable.", Toast.LENGTH_SHORT).show()
                return
            }
            if (ndef.maxSize < message.toByteArray().size) {
                Toast.makeText(this, "Message is too large for the tag.", Toast.LENGTH_SHORT).show()
                return
            }
            ndef.writeNdefMessage(message)
            Toast.makeText(this, "Phone number written to tag.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to write to tag.", Toast.LENGTH_SHORT).show()
        } finally {
            ndef.close()
        }
    }
}
