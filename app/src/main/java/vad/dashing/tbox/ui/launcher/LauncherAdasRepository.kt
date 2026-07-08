package vad.dashing.tbox.ui.launcher

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanEngineFacade

/**
 * Live ADAS state for the launcher (FRM target object + LKA lanes).
 * Registers mbCAN push listeners while the launcher is visible.
 */
object LauncherAdasRepository {
    private const val ENGINE_CLASS = "com.mengbo.mbCan.MBCanEngine"
    private const val FRM_INFO_CLASS = "com.mengbo.mbCan.entity.MBCanVehicleFrmDectInfo"
    private const val LKA_STATUS_CLASS = "com.mengbo.mbCan.entity.MBCanVehicleLkaSlaStatus"

    private var active = false
    private var frmInfoListenerProxy: Any? = null
    private var lkaStatusListenerProxy: Any? = null

    private val _state = MutableStateFlow(LauncherAdasState())
    val state: StateFlow<LauncherAdasState> = _state.asStateFlow()

    private var frmAccMode: Byte = 0
    private var frmVSetDis: Byte = 0
    private var frmDxTarObj: Byte = 0
    private var frmObjValid: Byte = 0
    private var frmFrontObjectType: Byte = 0
    private var frmTextInfo: Byte = 0
    private var frmTakeOverReq: Byte = 0
    private var frmObjectDx: Byte = 0
    private var frmFcwPreWarning: Byte = 0
    private var frmDistanceWarning: Byte = 0
    private var frmTimeGap: Byte = 0
    private var lkaLeft: Byte = 0
    private var lkaRight: Byte = 0
    private var lkaAdasTakeOver: Byte = 0

    fun ensureActive() {
        if (active) return
        if (MbCanEngineFacade.ensureInitialized() !is MbCanAvailability.Available) return
        registerFrmListener()
        registerLkaListener()
        active = frmInfoListenerProxy != null || lkaStatusListenerProxy != null
    }

    fun stop() {
        if (!active) return
        unregisterFrmListener()
        unregisterLkaListener()
        active = false
        _state.value = LauncherAdasState()
        clearSnapshots()
    }

    private fun registerFrmListener() {
        if (frmInfoListenerProxy != null) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCanVehicleFrmDectInfoCallback")
        }.getOrNull() ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "onCanVehicleFrmInfo" && args?.isNotEmpty() == true) {
                parseFrmInfo(args[0])?.let { onFrmInfo(it) }
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            engineClass.getMethod(
                "registIMBVehicleFrmDectInfoListener",
                iface,
            ).invoke(inst, proxy)
            frmInfoListenerProxy = proxy
        }
    }

    private fun unregisterFrmListener() {
        val inst = engineInstance()
        if (inst != null && frmInfoListenerProxy != null) {
            runCatching {
                Class.forName(ENGINE_CLASS)
                    .getMethod("unRegistIMBVehicleFrmDectInfoListener")
                    .invoke(inst)
            }
        }
        frmInfoListenerProxy = null
    }

    private fun registerLkaListener() {
        if (lkaStatusListenerProxy != null) return
        val inst = engineInstance() ?: return
        val iface = runCatching {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCanVehicleLkaSlaStatusCallback")
        }.getOrNull() ?: return
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _, method, args ->
            if (method.name == "onVehicleLkaSlaStatus" && args?.isNotEmpty() == true) {
                parseLkaStatus(args[0])?.let { onLkaStatus(it) }
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            engineClass.getMethod(
                "registIMBCanVehicleLkaSlaStatusListener",
                iface,
            ).invoke(inst, proxy)
            lkaStatusListenerProxy = proxy
        }
    }

    private fun unregisterLkaListener() {
        val inst = engineInstance()
        if (inst != null && lkaStatusListenerProxy != null) {
            runCatching {
                Class.forName(ENGINE_CLASS)
                    .getMethod("unRegistIMBCanVehicleLkaSlaStatusListener")
                    .invoke(inst)
            }
        }
        lkaStatusListenerProxy = null
    }

    private fun engineInstance(): Any? = runCatching {
        Class.forName(ENGINE_CLASS).getMethod("getInstance").invoke(null)
    }.getOrNull()

    private data class FrmSnapshot(
        val accMode: Byte,
        val vSetDis: Byte,
        val dxTarObj: Byte,
        val objValid: Byte,
        val frontObjectType: Byte,
        val textInfo: Byte,
        val takeOverReq: Byte,
        val objectDx: Byte,
        val fcwPreWarning: Byte,
        val distanceWarning: Byte,
        val timeGap: Byte,
    )

    private data class LkaSnapshot(
        val leftVisualization: Byte,
        val rightVisualization: Byte,
        val adasTakeOverReq: Byte,
    )

    private fun parseFrmInfo(raw: Any?): FrmSnapshot? = runCatching {
        val cls = Class.forName(FRM_INFO_CLASS)
        FrmSnapshot(
            accMode = cls.getMethod("getFRM_3_ACCMode").invoke(raw) as Byte,
            vSetDis = cls.getMethod("getFRM_3_VSetDis").invoke(raw) as Byte,
            dxTarObj = cls.getMethod("getFRM_3_DxTarObj").invoke(raw) as Byte,
            objValid = cls.getMethod("getFRM_3_ObjValid").invoke(raw) as Byte,
            frontObjectType = cls.getMethod("getFRM_3_FrontObject_Type").invoke(raw) as Byte,
            textInfo = cls.getMethod("getFRM_3_Textinfo").invoke(raw) as Byte,
            takeOverReq = cls.getMethod("getFRM_3_TakeOverReq").invoke(raw) as Byte,
            objectDx = cls.getMethod("getFRM_3_Obiect_Dx").invoke(raw) as Byte,
            fcwPreWarning = cls.getMethod("getFRM_3_FCW_PreWarning").invoke(raw) as Byte,
            distanceWarning = cls.getMethod("getFRM_3_DistanceWarning").invoke(raw) as Byte,
            timeGap = cls.getMethod("getFRM_3_TimeGapSet_ICM").invoke(raw) as Byte,
        )
    }.getOrNull()

    private fun parseLkaStatus(raw: Any?): LkaSnapshot? = runCatching {
        val cls = Class.forName(LKA_STATUS_CLASS)
        LkaSnapshot(
            leftVisualization = cls.getMethod("getFCM_2_LDW_LKA_LeftVisualization").invoke(raw) as Byte,
            rightVisualization = cls.getMethod("getFCM_2_LDW_LKA_RightVisualization").invoke(raw) as Byte,
            adasTakeOverReq = cls.getMethod("getFCM_2_ADAS_TakeoverReq").invoke(raw) as Byte,
        )
    }.getOrNull()

    private fun onFrmInfo(snapshot: FrmSnapshot) {
        frmAccMode = snapshot.accMode
        frmVSetDis = snapshot.vSetDis
        frmDxTarObj = snapshot.dxTarObj
        frmObjValid = snapshot.objValid
        frmFrontObjectType = snapshot.frontObjectType
        frmTextInfo = snapshot.textInfo
        frmTakeOverReq = snapshot.takeOverReq
        frmObjectDx = snapshot.objectDx
        frmFcwPreWarning = snapshot.fcwPreWarning
        frmDistanceWarning = snapshot.distanceWarning
        frmTimeGap = snapshot.timeGap
        publish()
    }

    private fun onLkaStatus(snapshot: LkaSnapshot) {
        lkaLeft = snapshot.leftVisualization
        lkaRight = snapshot.rightVisualization
        lkaAdasTakeOver = snapshot.adasTakeOverReq
        publish()
    }

    private fun publish() {
        _state.value = buildLauncherAdasState(
            accModeRaw = frmAccMode,
            vSetDisRaw = frmVSetDis,
            objValidRaw = frmObjValid,
            frontObjectTypeRaw = frmFrontObjectType,
            dxTarObjRaw = frmDxTarObj,
            objectDxRaw = frmObjectDx,
            takeOverRaw = frmTakeOverReq,
            textInfoRaw = frmTextInfo,
            fcwPreWarningRaw = frmFcwPreWarning,
            distanceWarningRaw = frmDistanceWarning,
            timeGapRaw = frmTimeGap,
            leftLaneRaw = lkaLeft,
            rightLaneRaw = lkaRight,
            adasTakeOverRaw = lkaAdasTakeOver,
        )
    }

    private fun clearSnapshots() {
        frmAccMode = 0
        frmVSetDis = 0
        frmDxTarObj = 0
        frmObjValid = 0
        frmFrontObjectType = 0
        frmTextInfo = 0
        frmTakeOverReq = 0
        frmObjectDx = 0
        frmFcwPreWarning = 0
        frmDistanceWarning = 0
        frmTimeGap = 0
        lkaLeft = 0
        lkaRight = 0
        lkaAdasTakeOver = 0
    }
}
