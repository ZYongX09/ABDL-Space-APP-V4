package top.abdl_space.app.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** 可注入的调度器集合（测试可替换；DI 绑定在上层模块提供）。 */
class AppDispatchers {
    val io: CoroutineDispatcher = Dispatchers.IO
    val default: CoroutineDispatcher = Dispatchers.Default
    val main: CoroutineDispatcher = Dispatchers.Main
}
