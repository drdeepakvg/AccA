package mattecarra.accapp.activities

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mattecarra.accapp.models.AccConfig
import mattecarra.accapp.models.AccaProfile
import mattecarra.accapp.utils.AccUtils
import mattecarra.accapp.utils.ConfigUtils
import mattecarra.accapp.utils.Constants
import mattecarra.accapp.utils.DataRepository
import kotlin.coroutines.CoroutineContext

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    val mDataRepository: DataRepository
    private val mSharedPrefs: SharedPreferences
    private val config: MutableLiveData<AccConfig> = MutableLiveData()

    private var mParentJob = Job()
    private val mCoroutineContext: CoroutineContext
        get() = mParentJob + Dispatchers.IO

    private val mScope = CoroutineScope(mCoroutineContext)

    init {
        mDataRepository = DataRepository(application, mScope)
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(application)

        try {
            this.config.value = AccUtils.readConfig()
        } catch (ex: Exception) {
            ex.printStackTrace()
            //TODO showConfigReadError()
            this.config.value = AccUtils.defaultConfig //if mAccConfig is null I use default mAccConfig values.
        }
    }

    /**
     * Sets an observer for config.
     */
    fun observeConfig(owner: LifecycleOwner, observer: Observer<AccConfig>) {
        config.observe(owner, observer)
    }

    /*
    * This method is designed to get a parameter from AccConfig or sAccConfig itself
    * Example:
    * val parameter = getAccConfigValue { it.oneParameter }
    * */
    fun <T> getAccConfigValue(callback: (AccConfig) -> T): T {
        return callback(config.value!!)
    }

    /*
    * This method is designed to set a parameter of AccConfig and write on file
    * Example:
    * updateAccConfigValue { config ->
    *   config.oneParameter = 1
    * }
    * */
    fun updateAccConfigValue(callback: (AccConfig) -> Unit) {
        val value = config.value!!

        callback(value)

        this.config.postValue(value)
        saveAccConfig(value)
    }

    /*
    * Updates the AccConfig and write on file
    * */
    fun updateAccConfig(value: AccConfig) {
        config.postValue(value)
        saveAccConfig(value)
    }

    /*
    * Saves config on file. It's run in an async thread every time config is updated.
    * It's synchronized to prevent weird race conditions.
    * */
    private val saveConfigLock = Object()
    private fun saveAccConfig(value: AccConfig) = mScope.launch {
        synchronized(saveConfigLock) {
            val res = ConfigUtils.updateAcc(value)

            if(!res.isSuccessful()) {
                res.debug()
                //TODO
                /*if (!result.voltControlUpdateSuccessful) {
                    Toast.makeText(this@MainActivity, R.string.wrong_volt_file, Toast.LENGTH_LONG).show()
                }*/

                val currentConfigVal = try {
                    AccUtils.readConfig()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    AccUtils.defaultConfig
                }

                config.postValue(currentConfigVal)
            }
        }
    }


    fun insertProfile(profile: AccaProfile) = mScope.launch(Dispatchers.IO) {
        mDataRepository.insertProfile(profile)
    }

    fun deleteProfile(profile: AccaProfile) = mScope.launch(Dispatchers.IO) {
        mDataRepository.deleteProfile(profile)
    }

    fun updateProfile(profile: AccaProfile) = mScope.launch(Dispatchers.IO) {
        mDataRepository.updateProfile(profile)
    }

    fun getProfileById(id: Int) : AccaProfile {
        return mDataRepository.getProfileById(id)
    }

    /**
     * Clears the currently selected profile ID from Shared Preferences.
     */
    fun clearCurrentSelectedProfile() {
        mSharedPrefs.edit().putInt(Constants.PROFILE_KEY, -1).apply()
    }

    /**
     * Sets the profile ID to the profile key in the app's shared preferences.
     * @param profileId ID of the profile selected.
     */
    fun setCurrentSelectedProfile(profileId: Int) {
        mSharedPrefs.edit().putInt(Constants.PROFILE_KEY, profileId).apply()
    }
}