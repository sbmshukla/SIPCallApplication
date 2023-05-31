package com.example.sipcallapplication

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.databinding.DataBindingUtil
import com.example.sipcallapplication.databinding.ActivityMainBinding
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Content
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Event
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType


class MainActivity : AppCompatActivity() {

    val INCOMING_CALL_NOTIFICATION_CHANNEL_ID = "10001"
    val default_incoming_notification_channel_id = "default"


    val ONGOING_CALL_NOTIFICATION_CHANNEL_ID = "10002"
    val default_ongoing_notification_channel_id = "default"

//    val MISSED_CALL_NOTIFICATION_CHANNEL_ID = "10003"
//    val default_missed_notification_channel_id = "default"

    var incomingCallNotificationManager: NotificationManager? = null
    var ongoingCallNotificationManager: NotificationManager? = null
//    var missedCallNotificationManager: NotificationManager? = null

//    var incomingCallReceived:Boolean=false

    private lateinit var core: Core

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,R.layout.activity_main)

        // Awake Screen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // We will need the RECORD_AUDIO permission for video call
        if (packageManager.checkPermission(Manifest.permission.RECORD_AUDIO, packageName) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
            return
        }

        val factory = Factory.instance()
        factory.setDebugMode(true, "SIP Calling")
        core = factory.createCore(null, null, this)

        // Make sure the core is configured to use push notification token from firebase
        core.isPushNotificationEnabled = true

        binding.btnSipLogin.setOnClickListener {
            login()
        }

        binding.incomingHangUp.setOnClickListener {
            // Terminates the call, whether it is ringing or running
            core.currentCall?.terminate()
        }

       binding.incomingAnswer.setOnClickListener {
            // if we wanted, we could create a CallParams object
            // and answer using this object to make changes to the call configuration
            // (see OutgoingCall tutorial)
            core.currentCall?.accept()
        }

        binding.incomingMuteMic.setOnClickListener {
            // The following toggles the microphone, disabling completely / enabling the sound capture
            // from the device microphone
            core.enableMic(!core.micEnabled())
        }

        binding.incomingToggleSpeaker.setOnClickListener {
            toggleSpeaker()
        }

        binding.outgoingAnswer.setOnClickListener {
            outgoingCall()
        }

        binding.outgoingPause.setOnClickListener {
            pauseOrResume()
        }

        binding.outgoingHangUp.setOnClickListener {
            hangUp()
        }

    }

    private fun toggleSpeaker() {
        // Get the currently used audio device
        val currentAudioDevice = core.currentCall?.outputAudioDevice
        val speakerEnabled = currentAudioDevice?.type == AudioDevice.Type.Speaker
        // We can get a list of all available audio devices using
        // Note that on tablets for example, there may be no Earpiece device
        for (audioDevice in core.audioDevices) {
            if (speakerEnabled && audioDevice.type == AudioDevice.Type.Earpiece) {
                core.currentCall?.outputAudioDevice = audioDevice
                return
            } else if (!speakerEnabled && audioDevice.type == AudioDevice.Type.Speaker) {
                core.currentCall?.outputAudioDevice = audioDevice
                return
            } // If we wanted to route the audio to a bluetooth headset
            else if (audioDevice.type == AudioDevice.Type.Bluetooth) {
                core.currentCall?.outputAudioDevice = audioDevice
            }
        }
    }

    private fun login() {
        val username = binding.username.text.toString()
        val password = binding.password.text.toString()
        val domain = binding.domain.text.toString()
        val transportType = when (binding.rgNetwork.checkedRadioButtonId) {
            R.id.rb_udp -> TransportType.Udp
            R.id.rb_tcp -> TransportType.Tcp
            R.id.rb_tls -> TransportType.Tls
            else -> TransportType.Tls
        }
        val authInfo = Factory.instance().createAuthInfo(username, null, password, null, null, domain, null)

        val params = core.createAccountParams()
        val identity = Factory.instance().createAddress("sip:$username@$domain")
        params.identityAddress = identity

        val address = Factory.instance().createAddress("sip:$domain")
        address?.transport = transportType
        params.serverAddress = address
        params.registerEnabled = true
        val account = core.createAccount(params)

        core.addAuthInfo(authInfo)
        core.addAccount(account)

        core.defaultAccount = account
        core.addListener(coreListener)
        core.start()

        if (!core.isPushNotificationAvailable) {
            Toast.makeText(this, "Something is wrong with the push setup!", Toast.LENGTH_LONG).show()
        }
    }

    private val coreListener = object: CoreListenerStub() {

        override fun onAccountRegistrationStateChanged(core: Core, account: Account, state: RegistrationState?, message: String) {

            binding.tvStatus.text = message

            when (state) {
                RegistrationState.Failed -> {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
                RegistrationState.Ok -> {
                    binding.llLogin.visibility = View.GONE
                    binding.llCalling.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    // This will display the push information stored in the contact URI parameters
                    Toast.makeText(this@MainActivity, account.params.contactUriParameters, Toast.LENGTH_SHORT).show()
                }
                RegistrationState.Progress -> {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }

                else -> {
                }
            }
        }

        override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) {
            // This callback will be triggered when a successful audio device has been changed
        }

        override fun onAudioDevicesListUpdated(core: Core) {
            // This callback will be triggered when the available devices list has changed,
            // for example after a bluetooth headset has been connected/disconnected.
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State?, message: String) {

            binding.callStatus.text = message

            // When a call is received
            when (state) {
                Call.State.IncomingReceived -> {
                    binding.llIncoming.visibility = View.VISIBLE
                    binding.llOutgoing.visibility = View.GONE
                    binding.incomingMuteMic.setImageResource(R.drawable.baseline_mic_50)
                    binding.incomingToggleSpeaker.setImageResource(R.drawable.baseline_volume_off_50)
                    binding.remoteAddress.setText(call.remoteAddress.asStringUriOnly())

//                    incomingCallReceived=false
                    createIncomingNotification(window.decorView.rootView,"Incoming Call...",call.remoteAddress.asStringUriOnly())
                }
                Call.State.Connected -> {
                    binding.incomingMuteMic.setImageResource(R.drawable.baseline_mic_50)
                    binding.incomingToggleSpeaker.setImageResource(R.drawable.baseline_volume_off_50)
                    removeIncomingCallNotification(window.decorView.rootView)

//                    incomingCallReceived=true
                    createOngoingNotification(window.decorView.rootView,"Call Ongoing","incoming call ongoing...")
                }
                Call.State.Released -> {
                    binding.llIncoming.visibility = View.GONE
                    binding.llOutgoing.visibility = View.VISIBLE
                    binding.incomingMuteMic.setImageResource(R.drawable.baseline_mic_50)
                    binding.incomingToggleSpeaker.setImageResource(R.drawable.baseline_volume_up_50)
                    binding.remoteAddress.text.clear()

                    removeIncomingCallNotification(window.decorView.rootView)
                    removeOngoingCallNotification(window.decorView.rootView)


//                    createMissedCallNotification(window.decorView.rootView,"Missed Call Received...",call.remoteAddress.asStringUriOnly())

                }
                Call.State.OutgoingInit -> {
                    // First state an outgoing call will go through
                }
                Call.State.OutgoingProgress -> {
                    // Right after outgoing init
                }
                Call.State.OutgoingRinging -> {
                    // This state will be reached upon reception of the 180 RINGING
                }
                Call.State.StreamsRunning -> {
                    // This state indicates the call is active.
                    // You may reach this state multiple times, for example after a pause/resume
                    // or after the ICE negotiation completes
                    // Wait for the call to be connected before allowing a call update
                    binding.outgoingPause.setImageResource(R.drawable.baseline_resume_50)
                }
                Call.State.Paused -> {
                    // When you put a call in pause, it will became Paused
                    binding.outgoingPause.setImageResource(R.drawable.baseline_pause_50)
                }
                Call.State.PausedByRemote -> {
                    // When the remote end of the call pauses it, it will be PausedByRemote
                }
                Call.State.Updating -> {
                    // When we request a call update, for example when toggling video
                }
                Call.State.UpdatedByRemote -> {
                    // When the remote requests a call update
                }
                Call.State.Error -> {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        override fun onNotifyReceived(
            core: Core,
            linphoneEvent: Event,
            notifiedEvent: String,
            body: Content
        ) {
            super.onNotifyReceived(core, linphoneEvent, notifiedEvent, body)
            customNotification()
        }
    }

    /*
    private fun createMissedCallNotification(rootView: View?, state: String, asStringUriOnly: String) {
        missedCallNotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val mBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            this@MainActivity,
            default_missed_notification_channel_id
        ).setContentTitle(state).setContentText(asStringUriOnly).setTicker("CSK Won The Final - 2023")
            .setSmallIcon(R.drawable.ic_call).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                NotificationCompat.CATEGORY_CALL
            ).setAutoCancel(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val notificationChannel = NotificationChannel(
                MISSED_CALL_NOTIFICATION_CHANNEL_ID,
                "NOTIFICATION_CHANNEL_NAME",
                importance
            )
            mBuilder.setChannelId(MISSED_CALL_NOTIFICATION_CHANNEL_ID)
            assert(missedCallNotificationManager != null)
            missedCallNotificationManager!!.createNotificationChannel(notificationChannel)
        }
        assert(missedCallNotificationManager != null)
        missedCallNotificationManager!!.notify(System.currentTimeMillis().toInt(), mBuilder.build())
    }
     */

    private fun createIncomingNotification(rootView: View?, caller: String, state: String) {
        val myIntent = Intent(this@MainActivity, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this@MainActivity,
            0,
            myIntent,
            PendingIntent.FLAG_IMMUTABLE
        )


        incomingCallNotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val mBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            this@MainActivity,
            default_incoming_notification_channel_id
        ).setContentTitle(caller).setContentText(state).setTicker("CSK Won The Final - 2023")
            .setSmallIcon(R.drawable.ic_call).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                NotificationCompat.CATEGORY_CALL
            ).setAutoCancel(false).setOngoing(true).setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val notificationChannel = NotificationChannel(
                INCOMING_CALL_NOTIFICATION_CHANNEL_ID,
                "NOTIFICATION_CHANNEL_NAME",
                importance
            )
            mBuilder.setChannelId(INCOMING_CALL_NOTIFICATION_CHANNEL_ID)
            assert(incomingCallNotificationManager != null)
            incomingCallNotificationManager!!.createNotificationChannel(notificationChannel)
        }
        assert(incomingCallNotificationManager != null)
        incomingCallNotificationManager!!.notify(System.currentTimeMillis().toInt(), mBuilder.build())

    }

    private fun createOngoingNotification(v: View, callString: String, state: String) {
        ongoingCallNotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val mBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            this@MainActivity,
            default_ongoing_notification_channel_id
        )
        mBuilder.setContentTitle(callString).setContentText(state)
            .setTicker("CSK Won The Final - 2023").setSmallIcon(R.drawable.ic_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL).setAutoCancel(false).setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_MIN
            val notificationChannel = NotificationChannel(
                ONGOING_CALL_NOTIFICATION_CHANNEL_ID,
                "NOTIFICATION_CHANNEL_NAME",
                importance
            )
            mBuilder.setChannelId(ONGOING_CALL_NOTIFICATION_CHANNEL_ID)
            assert(ongoingCallNotificationManager != null)
            ongoingCallNotificationManager!!.createNotificationChannel(notificationChannel)
        }
        assert(ongoingCallNotificationManager != null)
        ongoingCallNotificationManager!!.notify(
            System.currentTimeMillis().toInt(),
            mBuilder.build()
        )
    }

    fun customNotification() {
        val remoteViews = RemoteViews(packageName, R.layout.customnotification)
        val strtitle = getString(R.string.app_name)
        val strtext = getString(R.string.customnotificationtitle)
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("title", strtitle)
        intent.putExtra("text", strtext)
        val pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val builder: Notification.Builder? = Notification.Builder(this)
            .setSmallIcon(R.drawable.baseline_calling_24)
            .setAutoCancel(true).setContentIntent(pIntent)
            .setContent(remoteViews)
        remoteViews.setImageViewResource(R.id.imagenotileft, R.drawable.baseline_calling_24)
        remoteViews.setImageViewResource(R.id.imagenotiright, R.drawable.baseline_person_24)
        remoteViews.setTextViewText(R.id.title, getString(R.string.customnotificationtitle))
        remoteViews.setTextViewText(R.id.text, getString(R.string.customnotificationtext))
        // Create Notification Manager
        val notificationmanager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Build Notification with Notification Manager
        notificationmanager.notify(0, builder!!.build())
    }

    private fun outgoingCall() {
        // As for everything we need to get the SIP URI of the remote and convert it to an Address
        val remoteSipUri = binding.remoteAddress.text.toString()
        val uri = "sip:$remoteSipUri@${binding.domain.text}"
        val remoteAddress = Factory.instance().createAddress(uri)
        remoteAddress ?: return // If address parsing fails, we can't continue with outgoing call process

        // We also need a CallParams object
        // Create call params expects a Call object for incoming calls, but for outgoing we must use null safely
        val params = core.createCallParams(null)
        params ?: return // Same for params

        // We can now configure it
        // Here we ask for no encryption but we could ask for ZRTP/SRTP/DTLS
        params.mediaEncryption = MediaEncryption.None
        // If we wanted to start the call with video directly
        params.enableVideo(true)

        // Finally we start the call
        core.inviteAddressWithParams(remoteAddress, params)
        // Call process can be followed in onCallStateChanged callback from core listener
    }

    private fun hangUp() {
        if (core.callsNb == 0) return

        // If the call state isn't paused, we can get it using core.currentCall
        val call = if (core.currentCall != null) core.currentCall else core.calls[0]
        call ?: return

        // Terminating a call is quite simple
        call.terminate()
    }

    private fun pauseOrResume() {
        if (core.callsNb == 0) return
        val call = if (core.currentCall != null) core.currentCall else core.calls[0]
        call ?: return

        if (call.state != Call.State.Paused && call.state != Call.State.Pausing) {
            // If our call isn't paused, let's pause it
            call.pause()
            binding.outgoingPause.setImageResource(R.drawable.baseline_pause_50)
        } else if (call.state != Call.State.Resuming) {
            // Otherwise let's resume it
            call.resume()
            binding.outgoingPause.setImageResource(R.drawable.baseline_resume_50)
        }
    }


    fun removeIncomingCallNotification(view: View?) {
        incomingCallNotificationManager!!.cancelAll()
    }

    fun removeOngoingCallNotification(view: View?) {
        ongoingCallNotificationManager!!.cancelAll()
    }

}