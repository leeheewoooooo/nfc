package com.example.tel

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AdminActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFilters: Array<IntentFilter>
    private lateinit var textView: TextView
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var databaseReference: DatabaseReference
    private lateinit var phoneNumberListener: ValueEventListener

    // To keep track of displayed phone numbers
    private val displayedPhoneNumbers = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        textView = findViewById(R.id.textView)

        firebaseDatabase = FirebaseDatabase.getInstance()
        databaseReference = firebaseDatabase.getReference("phoneNumber")

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val intent = Intent(this, javaClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        val tagDetected = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        intentFilters = arrayOf(tagDetected)

        setupFirebaseListener()
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
                readNfcTag(it)
            }
        }
    }

    private fun readNfcTag(tag: Tag) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val ndefMessage = ndef.cachedNdefMessage
                if (ndefMessage == null) {
                    Toast.makeText(this, "NDEF Message is null", Toast.LENGTH_SHORT).show()
                    return
                }

                val records = ndefMessage.records
                if (records.isNotEmpty()) {
                    val payload = records[0].payload
                    val text = String(payload, Charsets.UTF_8)
                    textView.text = text
                    Toast.makeText(this, "Read data: $text", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No records found in the tag", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to read tag", Toast.LENGTH_SHORT).show()
            } finally {
                ndef.close()
            }
        } else {
            Toast.makeText(this, "NDEF is not supported on this tag.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFirebaseListener() {
        phoneNumberListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val phoneNumbers = StringBuilder()
                val newPhoneNumbers = mutableSetOf<String>()

                dataSnapshot.children.forEach { snapshot ->
                    val phoneNumber = snapshot.getValue(String::class.java)
                    phoneNumber?.let {
                        if (!displayedPhoneNumbers.contains(it)) {
                            newPhoneNumbers.add(it)
                            displayedPhoneNumbers.add(it)
                        }
                    }
                }

                if (newPhoneNumbers.isNotEmpty()) {
                    newPhoneNumbers.forEach { phoneNumber ->
                        phoneNumbers.append(phoneNumber).append("\n")
                    }
                    textView.text = phoneNumbers.toString()
                    Toast.makeText(this@AdminActivity, "Phone numbers updated", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@AdminActivity, "Failed to fetch phone numbers from Firebase", Toast.LENGTH_SHORT).show()
                databaseError.toException().printStackTrace()
            }
        }
        databaseReference.addValueEventListener(phoneNumberListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        databaseReference.removeEventListener(phoneNumberListener)
    }
}
