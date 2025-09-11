package com.dashing.tbox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var tboxConnection: TextView
    private lateinit var textMessage: TextView
    private lateinit var textCSQValue: TextView
    private lateinit var textIMEIValue: TextView
    private lateinit var textICCIDValue: TextView
    private lateinit var textOperatorValue: TextView
    private lateinit var textRegistrationStatus: TextView
    private lateinit var textSIMStatus: TextView
    private lateinit var textNetStatus: TextView
    private lateinit var textAPNStatus: TextView
    private lateinit var textAPNType: TextView
    private lateinit var textAPNIP: TextView
    private lateinit var textAPN2Status: TextView
    private lateinit var textAPN2Type: TextView
    private lateinit var textAPN2IP: TextView
    private lateinit var buttonModemCheck: Button
    private lateinit var buttonModemOn: Button
    private lateinit var buttonModemOff: Button
    private lateinit var buttonTboxReboot: Button
    private lateinit var buttonAPN1Restart: Button
    private lateinit var buttonAPN1Fly: Button
    private lateinit var buttonAPN1Reconnect: Button
    private lateinit var buttonAPN2Restart: Button
    private lateinit var buttonAPN2Fly: Button
    private lateinit var buttonAPN2Reconnect: Button
    private lateinit var buttontest1: Button
    private lateinit var buttontest2: Button
    private lateinit var buttontest3: Button
    private lateinit var numberPin: EditText
    private lateinit var numberPuk: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tboxConnection = findViewById(R.id.tboxConnection)
        textMessage = findViewById(R.id.txtMessage)
        textCSQValue = findViewById(R.id.csqValue)
        textIMEIValue = findViewById(R.id.imeiValue)
        textICCIDValue = findViewById(R.id.iccidValue)
        textOperatorValue = findViewById(R.id.operatorValue)
        textRegistrationStatus = findViewById(R.id.registrationStatus)
        textSIMStatus = findViewById(R.id.simStatus)
        textNetStatus = findViewById(R.id.netStatus)
        textAPNStatus = findViewById(R.id.apnStatus)
        textAPNType = findViewById(R.id.apnType)
        textAPNIP = findViewById(R.id.apnIP)
        textAPN2Status = findViewById(R.id.apn2Status)
        textAPN2Type = findViewById(R.id.apn2Type)
        textAPN2IP = findViewById(R.id.apn2IP)
        buttonModemCheck = findViewById(R.id.btnModemCheck)
        buttonModemOn = findViewById(R.id.btnModemOn)
        buttonModemOff = findViewById(R.id.btnModemOff)
        buttonTboxReboot = findViewById(R.id.btnTboxReboot)
        buttonAPN1Restart = findViewById(R.id.btnAPN1Restart)
        buttonAPN1Fly = findViewById(R.id.btnAPN1Fly)
        buttonAPN1Reconnect = findViewById(R.id.btnAPN1Reconnect)
        buttonAPN2Restart = findViewById(R.id.btnAPN2Restart)
        buttonAPN2Fly = findViewById(R.id.btnAPN2Fly)
        buttonAPN2Reconnect = findViewById(R.id.btnAPN2Reconnect)
        buttontest1 = findViewById(R.id.btntest1)
        buttontest2 = findViewById(R.id.btntest2)
        buttontest3 = findViewById(R.id.btntest3)

        numberPin = findViewById(R.id.pin)
        numberPuk = findViewById(R.id.puk)

        buttonModemCheck.setOnClickListener {
            modemCheck()
        }
        buttonModemOn.setOnClickListener {
            modemOn()
        }
        buttonModemOff.setOnClickListener {
            modemOff()
        }
        buttonTboxReboot.setOnClickListener {
            rebootTbox()
        }
        buttonAPN1Restart.setOnClickListener {
            apnManage(1, "restart")
        }
        buttonAPN1Fly.setOnClickListener {
            apnManage(1, "fly")
        }
        buttonAPN1Reconnect.setOnClickListener {
            apnManage(1, "reconnect")
        }
        buttonAPN2Restart.setOnClickListener {
            apnManage(2, "restart")
        }
        buttonAPN2Fly.setOnClickListener {
            apnManage(2, "fly")
        }
        buttonAPN2Reconnect.setOnClickListener {
            apnManage(2, "reconnect")
        }
        buttontest1.setOnClickListener {
            test(1)
        }
        buttontest2.setOnClickListener {
            test(2)
        }
        buttontest3.setOnClickListener {
            test(3)
        }
        startNetUpdater()
        //startAPNUpdater()
        setupStateFlowObservers()
    }

    override fun onRestart() {
        super.onRestart()
        startNetUpdater()
        //startAPNUpdater()
    }

    private fun setupStateFlowObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TboxRepository.tboxConnected.collect { tboxConnected ->
                    updateConnectionUI(tboxConnected)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TboxRepository.netState.collect { netState ->
                    updateSignalQualityUI(netState.csq)
                    textNetStatus.text = netState.netStatus
                    textSIMStatus.text = netState.simStatus
                    textRegistrationStatus.text = netState.regStatus
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TboxRepository.netValues.collect { netValues ->
                    textIMEIValue.text = netValues.imei
                    textICCIDValue.text = netValues.iccid
                    textOperatorValue.text = netValues.operator
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TboxRepository.apnState.collect { apnState ->
                    textAPNStatus.text = apnState.apnStatus
                    textAPNType.text = apnState.apnType
                    textAPNIP.text = apnState.apnIP
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TboxRepository.apn2State.collect { apnState ->
                    textAPN2Status.text = apnState.apnStatus
                    textAPN2Type.text = apnState.apnType
                    textAPN2IP.text = apnState.apnIP
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TboxRepository.message.collect { message ->
                    textMessage.append(message)
                    val scrollAmount = textMessage.layout?.getLineTop(textMessage.lineCount) ?: 0
                    if (scrollAmount > textMessage.height) {
                        textMessage.scrollTo(0, scrollAmount - textMessage.height)
                    }
                    val pattern = Regex("Результат:\\s*(.+)\\s*")
                    val matchResult = pattern.find(message)
                    val res = matchResult?.groupValues?.get(1) ?: ""
                    if (res.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, res, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateSignalQualityUI(csq: Int) {
        val color = when (csq) {
            in 0..10 -> Color.RED
            in 11..20 -> Color.YELLOW
            99 -> Color.RED
            else -> Color.GREEN
        }
        textCSQValue.setTextColor(color)
        if (csq == 99) {
            textCSQValue.text = "-"
        }
        else {
            textCSQValue.text = csq.toString()
        }
    }

    private fun updateConnectionUI(connected: Boolean) {
        val color = if (connected) Color.GREEN else Color.RED
        tboxConnection.setTextColor(color)
        tboxConnection.text = if (connected) "TBox подключен" else "TBox отключен"
    }

    private fun startNetUpdater() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_NET_UPD_START
        }
        startService(intent)
    }

    private fun startAPNUpdater() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_APN_UPD_START
        }
        startService(intent)
    }

    private fun modemCheck() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_MODEM_CHECK
        }
        startService(intent)
    }

    private fun modemOff() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_MODEM_OFF
        }
        startService(intent)
    }

    private fun modemOn() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_MODEM_ON
        }
        startService(intent)
    }

    private fun rebootTbox() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_TBOX_REBOOT
        }
        startService(intent)
    }

    private fun apnManage(number: Int, cmd: String) {
        val intent = Intent(this, BackgroundService::class.java).apply {
            when (cmd) {
                "restart" -> {
                    action = if (number == 1) {
                        BackgroundService.ACTION_APN1_RESTART
                    }
                    else {
                        BackgroundService.ACTION_APN2_RESTART
                    }
                }
                "fly" -> {
                    action = if (number == 1) {
                        BackgroundService.ACTION_APN1_FLY
                    }
                    else {
                        BackgroundService.ACTION_APN2_FLY
                    }
                }
                "reconnect" -> {
                    action = if (number == 1) {
                        BackgroundService.ACTION_APN1_RECONNECT
                    }
                    else {
                        BackgroundService.ACTION_APN2_RECONNECT
                    }
                }
            }
        }
        startService(intent)
    }

    private fun test(cmd: Int) {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = if (cmd == 2) {
                BackgroundService.ACTION_TEST2
            }
            else if (cmd == 3) {
                BackgroundService.ACTION_TEST3
            }
            else {
                BackgroundService.ACTION_TEST1
            }
            val pinText = numberPin.text.toString()
            if (cmd == 2 || cmd == 3) {
                if (pinText.isNotEmpty()) {
                    if (cmd == 2) {
                        putExtra(BackgroundService.EXTRA_PIN, pinText)
                    }
                    else {
                        val pukText = numberPuk.text.toString()
                        if (pukText.isNotEmpty()) {
                            putExtra(BackgroundService.EXTRA_PIN, pinText)
                            putExtra(BackgroundService.EXTRA_PUK, pukText)
                        }
                        else {
                            Toast.makeText(this@MainActivity, "Не введен PUK", Toast.LENGTH_SHORT).show()
                            return
                        }
                    }
                }
                else {
                    Toast.makeText(this@MainActivity, "Не введен PIN", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        //stopAPNUpdater()
        stopNetUpdaterIfNoClients()
    }

    private fun stopAPNUpdater() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_APN_UPD_STOP
        }
        startService(intent)
    }

    private fun stopNetUpdaterIfNoClients() {
        if (!WidgetUtils.isWidgetActive(this)) {
            val intent = Intent(this, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_NET_UPD_STOP
            }
            startService(intent)
        }
    }
}
