package com.example.sipcallapplication

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.databinding.DataBindingUtil
import com.example.sipcallapplication.databinding.ActivityIncomingCallBinding
import com.example.sipcallapplication.databinding.ActivityMainBinding


class IncomingCallActivity : AppCompatActivity() {


    var sipCallerId: String? = null


    lateinit var binding: ActivityIncomingCallBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(
            this@IncomingCallActivity, R.layout.activity_incoming_call
        )


        val intent = intent
        if (intent != null) {
            sipCallerId = intent.getStringExtra("sipId")
            // Use the received data as needed
            // ...
        }

        val mainActivity = MainActivity()

        binding.apply {
            idSipId.text = sipCallerId

            btnDecline.setOnClickListener {

//               mainActivity.hangUp()

            }

            btnAnswer.setOnClickListener {
                //               mainActivity.hangUp()
            }
        }


    }
}