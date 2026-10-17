package com.shanhai.core.logic

import com.shanhai.core.model.Rarity
import com.shanhai.core.data.defaultRandom

/**
 * AR 收妖控制器（GDD 四·AR 可选）。
 *
 * ⚠️ **架构占位**：摄像头 / ARKit / ARCore 由平台层实现，本文件只定义
 * shared 与平台 AR 层之间的**契约**与复用 CaptureEngine 的点按判定逻辑。
 * - [status] 默认返回 UNSUPPORTED，平台应在获得相机权限且设备支持时返回 READY；
 *   拒绝授权或不支持 AR 时，UI 自动切「国风卷轴背景」，操作完全一致（不依赖陀螺仪/甩动）。
 * - [resolveAtAnchor] 与 [CaptureEngine.resolve] 同义：在 AR 锚点上完成一次点按收妖判定。
 */
object ARCaptureController {

    enum class ARStatus {
        /** 不支持 / 未初始化（占位默认） */
        UNSUPPORTED,
        /** 用户拒绝相机权限 */
        PERMISSION_DENIED,
        /** 就绪，可进入 AR 收妖 */
        READY,
    }

    /** 当前 AR 可用状态（平台层应覆盖此实现） */
    fun status(): ARStatus = ARStatus.UNSUPPORTED

    /** 在 AR 锚点上收妖：复用 CaptureEngine 光环判定，返回收妖结果 */
    fun resolveAtAnchor(
        accuracy: Double,
        rarity: Rarity,
        assist: Boolean,
        rng: () -> Double = ::defaultRandom,
    ): CaptureResult = CaptureEngine.resolve(accuracy, rarity, assist, rng)
}

/**
 * AR 摄像头后端契约（平台层实现，如 ARKit / ARCore）。
 *
 * 复用本文件 [ARStatus] 与 [CaptureEngine.resolve]（[resolveAtAnchor] 已复用），
 * 作为 shared 与平台 AR 层之间的契约，避免双端逻辑漂移。
 * 所有方法均为平台占位默认实现：未接入摄像头能力时一律返回 UNSUPPORTED / false，
 * 由平台层在获得相机权限且设备支持时覆盖。
 */
interface ArCaptureBackend {
    /** 设备是否具备可用摄像头（平台层检测） */
    fun isCameraAvailable(): Boolean
    /** 请求相机权限，返回最新 [ARCaptureController.ARStatus]（平台层驱动系统弹窗） */
    fun requestPermission(): ARCaptureController.ARStatus
    /** 在指定锚点妖开启一次 AR 捕获会话；成功返回 true */
    fun beginSession(anchorMonsterId: String): Boolean
    /** 结束当前捕获会话 */
    fun endSession()
    /** 在 AR 锚点上完成一次收妖判定（复用 [CaptureEngine.resolve]，与 [resolveAtAnchor] 同义） */
    fun resolveCapture(
        accuracy: Double,
        rarity: Rarity,
        assist: Boolean,
        rng: () -> Double = ::defaultRandom,
    ): CaptureResult
}

