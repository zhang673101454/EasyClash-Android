package com.github.kr328.clash

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.common.util.intent

/** 节点已嵌进主界面 Tab，旧入口直接回主页。 */
class ProxyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(MainActivity::class.intent)
        finish()
    }
}
