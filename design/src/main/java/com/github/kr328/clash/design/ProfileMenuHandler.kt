package com.github.kr328.clash.design

import android.app.Dialog
import com.github.kr328.clash.service.model.Profile

/**
 * 订阅卡片菜单动作（主页与订阅页共用弹窗布局）。
 */
interface ProfileMenuHandler {
    fun requestUpdate(dialog: Dialog, profile: Profile)
    fun requestEdit(dialog: Dialog, profile: Profile)
    fun requestDuplicate(dialog: Dialog, profile: Profile)
    fun requestDelete(dialog: Dialog, profile: Profile)
}
