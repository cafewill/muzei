/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.muzei.wallpaper

import android.app.KeyguardManager
import android.arch.lifecycle.DefaultLifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.content.*
import android.support.v4.os.UserManagerCompat
import com.google.android.apps.muzei.MuzeiWallpaperService
import com.google.android.apps.muzei.settings.Prefs

/**
 * LifecycleObserver responsible for monitoring the state of the lock screen
 */
class LockscreenObserver(private val mContext: Context,
                         private val mEngine: MuzeiWallpaperService.MuzeiWallpaperEngine)
    : DefaultLifecycleObserver {

    private var lockScreenVisibleReceiverRegistered = false
    private val mLockScreenPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if (Prefs.PREF_DISABLE_BLUR_WHEN_LOCKED == key) {
            if (sp.getBoolean(Prefs.PREF_DISABLE_BLUR_WHEN_LOCKED, false)) {
                val intentFilter = IntentFilter()
                intentFilter.addAction(Intent.ACTION_USER_PRESENT)
                intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
                intentFilter.addAction(Intent.ACTION_SCREEN_ON)
                mContext.registerReceiver(mLockScreenVisibleReceiver, intentFilter)
                lockScreenVisibleReceiverRegistered = true
                // If the user is not yet unlocked (i.e., using Direct Boot), we should
                // immediately send the lock screen visible callback
                if (!UserManagerCompat.isUserUnlocked(mContext)) {
                    mEngine.lockScreenVisibleChanged(true)
                }
            } else if (lockScreenVisibleReceiverRegistered) {
                mContext.unregisterReceiver(mLockScreenVisibleReceiver)
                lockScreenVisibleReceiverRegistered = false
            }
        }
    }
    private val mLockScreenVisibleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> mEngine.lockScreenVisibleChanged(false)
                Intent.ACTION_SCREEN_OFF -> mEngine.lockScreenVisibleChanged(true)
                Intent.ACTION_SCREEN_ON -> {
                    val kgm = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (!kgm.inKeyguardRestrictedInputMode()) {
                        mEngine.lockScreenVisibleChanged(false)
                    }
                }
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        val sp = Prefs.getSharedPreferences(mContext)
        sp.registerOnSharedPreferenceChangeListener(mLockScreenPreferenceChangeListener)
        // Trigger the initial registration if needed
        mLockScreenPreferenceChangeListener.onSharedPreferenceChanged(sp,
                Prefs.PREF_DISABLE_BLUR_WHEN_LOCKED)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (lockScreenVisibleReceiverRegistered) {
            mContext.unregisterReceiver(mLockScreenVisibleReceiver)
        }
        Prefs.getSharedPreferences(mContext)
                .unregisterOnSharedPreferenceChangeListener(mLockScreenPreferenceChangeListener)
    }
}
