package vash.prog.dashingCanService

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import vash.prog.dashingcanservice.R
import com.mengbo.mbCan.MBCanEngine
import com.mengbo.mbCan.defines.MBCanDataType
import com.mengbo.mbCan.defines.MBVehicleProperty
import com.mengbo.mbCan.entity.MBCanVehicleAccStatus
import com.mengbo.mbCan.entity.MBCanVehicleBcmStatus
import com.mengbo.mbCan.entity.MBCanVehicleEngine
import com.mengbo.mbCan.entity.MBCanVehicleLkaSlaStatus
import com.mengbo.mbCan.entity.MBCanVehicleSpeed
import com.mengbo.mbconfig.MBConfigConstant
import com.mengbo.mbconfig.PropManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Находим кнопки и текстовые поля по их ID
        val button1: Button = findViewById(R.id.button1)
        val button2: Button = findViewById(R.id.button2)
        val button3: Button = findViewById(R.id.button3)
        val button4: Button = findViewById(R.id.button4)
        val textView1: TextView = findViewById(R.id.textView1)
        val textView2: TextView = findViewById(R.id.textView2)
        val editText3: EditText = findViewById(R.id.editText3) // Изменено на EditText
        val textView4: TextView = findViewById(R.id.textView4)

        // Устанавливаем обработчики нажатий для каждой кнопки
        button1.setOnClickListener {
            textView1.text = inMetod1().toString()
        }
        button2.setOnClickListener {
            textView2.text = inMetod2().toString()
        }
        button3.setOnClickListener {
            editText3.setText(inMetod3()) // Устанавливаем текст в EditText
        }
        button4.setOnClickListener {
            textView4.text = inMetod4().toString()
        }
    }

    // Заглушки для методов
    private fun inMetod1(): MBCanVehicleEngine? {
        var znach1 = MBCanEngine.getInstance().getMbCanData(22, MBCanVehicleEngine::class.java)
        return znach1
    }

    private fun inMetod2(): MBCanVehicleBcmStatus? {
        var znach2 = MBCanEngine.getInstance().getMbCanData(21, MBCanVehicleBcmStatus::class.java)
        return znach2
    }

    private fun inMetod3(): String? {
        var znach3 = PropManager.getInstance().getProp(MBConfigConstant.RO_MB_CAN_CONFIG)
        return znach3
    }

    private fun inMetod4(): String {
        val editText3: EditText = findViewById(R.id.editText3) // Изменено на EditText
        var znach4 = PropManager.getInstance().setProp(MBConfigConstant.RO_MB_CAN_CONFIG, editText3.text.toString())
        return znach4.toString()
    }
}