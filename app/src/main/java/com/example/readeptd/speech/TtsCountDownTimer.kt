package com.example.readeptd.speech

import android.os.CountDownTimer

// 创建一个独立的类
class TtsCountDownTimer(
    private val millisInFuture: Long,
    private val onTickCallback: (Long) -> Unit,
    private val onFinishCallback: () -> Unit
) : CountDownTimer(millisInFuture, 1000) {

    override fun onTick(millisUntilFinished: Long) {
        onTickCallback(millisUntilFinished)
    }

    override fun onFinish() {
        onFinishCallback()
    }
}
