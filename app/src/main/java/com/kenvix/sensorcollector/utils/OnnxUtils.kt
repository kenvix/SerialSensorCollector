package com.kenvix.sensorcollector.utils

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.EnumSet

val onnxEnvironment by lazy { OrtEnvironment.getEnvironment() }

suspend fun Context.onnxLoadModel(inputStream: InputStream): OrtSession {
    return withContext(Dispatchers.IO) {
        // 创建OrtEnvironment
        val opts = OrtSession.SessionOptions()
        // 启用NNAPI执行提供者
        val providers = OrtEnvironment.getAvailableProviders()
        Log.v("OnnxUtils", "Available infer providers: $providers")
        // 配置为优先考虑执行速度
        opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)

        if (providers.contains(OrtProvider.NNAPI)) {
            opts.addNnapi(EnumSet.of(NNAPIFlags.CPU_DISABLED))
        }

        // 从assets加载模型
        val modelBytes = runInterruptible { inputStream.readBytes() }
        return@withContext onnxEnvironment.createSession(modelBytes, opts)
    }
}

fun OrtSession.execute() {

}
