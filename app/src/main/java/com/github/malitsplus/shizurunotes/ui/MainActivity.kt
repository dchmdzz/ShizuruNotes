package com.github.malitsplus.shizurunotes.ui

import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.github.malitsplus.shizurunotes.R
import com.github.malitsplus.shizurunotes.common.*
import com.github.malitsplus.shizurunotes.databinding.ActivityMainBinding
import com.github.malitsplus.shizurunotes.db.DBHelper
import com.github.malitsplus.shizurunotes.ui.shared.SharedViewModelChara
import com.github.malitsplus.shizurunotes.ui.shared.SharedViewModelClanBattle
import com.github.malitsplus.shizurunotes.ui.shared.SharedViewModelEquipment
import com.github.malitsplus.shizurunotes.ui.shared.SharedViewModelQuest
import com.github.malitsplus.shizurunotes.user.UserSettings
import com.github.malitsplus.shizurunotes.utils.FileUtils
import com.github.malitsplus.shizurunotes.utils.LogUtils
import com.github.malitsplus.shizurunotes.utils.Utils
import com.google.android.material.snackbar.Snackbar
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(),
    UpdateManager.IActivityCallBack,
    SharedViewModelChara.MasterCharaCallBack
{
    private lateinit var sharedEquipment: SharedViewModelEquipment
    private lateinit var sharedChara: SharedViewModelChara
    private lateinit var sharedClanBattle: SharedViewModelClanBattle
    private lateinit var sharedQuest: SharedViewModelQuest
    private lateinit var binding: ActivityMainBinding

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(App.localeManager.setLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        initSingletonClass()
        setSharedViewModels()
        if (checkDbFile()) {
            loadData()
        } else {
            checkUpdate()
            sharedChara.charaList.value = mutableListOf()
        }
    }

    private fun initSingletonClass() {
        Utils.setApp(application)
        DBHelper.with(application)
        UserSettings.with(application)
        UpdateManager.with(this).setIActivityCallBack(this)
        I18N.application = application
    }

    private fun setSharedViewModels() {
        sharedEquipment = ViewModelProvider(this)[SharedViewModelEquipment::class.java].apply {
            equipmentMap.observe(this@MainActivity, Observer {
                if (it.isNotEmpty()) {
                    sharedChara.loadData(it)
                }
            })
        }
        sharedChara = ViewModelProvider(this)[SharedViewModelChara::class.java].apply {
            callBack = this@MainActivity
        }
        sharedClanBattle = ViewModelProvider(this)[SharedViewModelClanBattle::class.java]
        sharedQuest = ViewModelProvider(this)[SharedViewModelQuest::class.java]
    }

    override fun charaLoadFinished() {
        checkUpdate()
    }

    override fun dbDownloadFinished() {
        thread(start = true) {
            for (i in 1..50) {
                if (sharedEquipment.loadingFlag.value == false
                    && sharedChara.loadingFlag.value == false
                    && sharedClanBattle.loadingFlag.value == false
                    && sharedQuest.loadingFlag.value == false) {
                    synchronized(DBHelper::class.java){
                        UpdateManager.get().doDecompress()
                    }
                    break
                }
                Thread.sleep(100)
                if (i == 50) {
                    LogUtils.file(LogUtils.I, "DbDecompress", "Time out: 5s.")
                    UpdateManager.get().updateFailed()
                }
            }
        }
    }

    override fun dbUpdateFinished() {
        clearData()
        callBack?.changeTextHintVisibility(false)
        loadData()

    }

    override fun showSnackBar(@StringRes messageRes: Int) {
        Snackbar.make(binding.activityFrame, messageRes, Snackbar.LENGTH_LONG).show()
    }

    private fun clearData() {
        sharedEquipment.equipmentMap.value?.clear()
        sharedChara.charaList.value?.clear()
        sharedClanBattle.periodList.value?.clear()
        sharedClanBattle.dungeonList.clear()
        sharedQuest.questList.value?.clear()
        sharedEquipment.selectedDrops.value?.clear()
    }

    private fun checkDbFile(): Boolean {
        return FileUtils.checkFileAndSize(FileUtils.getDbFilePath(), 50)
    }

    private fun checkUpdate() {
        UpdateManager.get().checkAppVersion(true)
    }

    private fun loadData() {
        sharedEquipment.loadData()
    }

    var callBack: IMainActivityCallBack? = null
    interface IMainActivityCallBack {
        fun changeTextHintVisibility(visible: Boolean)
    }
}
