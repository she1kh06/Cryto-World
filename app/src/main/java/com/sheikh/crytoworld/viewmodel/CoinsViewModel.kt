package com.sheikh.crytoworld.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.google.gson.Gson
import com.sheikh.crytoworld.database.AppDatabase
import com.sheikh.crytoworld.pojos.coin_full_info.CoinPriceInfo
import com.sheikh.crytoworld.pojos.coin_full_info.CoinPriceInfoRawData
import com.sheikh.crytoworld.retorfit.ApiFactory
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class CoinsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val compositeDisposable = CompositeDisposable()

    val coinPriceInfo: LiveData<List<CoinPriceInfo>> = database.getDao().getListOfCoinPriceData()

    fun getCoinDetailData(fSym: String): LiveData<CoinPriceInfo> {
        return database.getDao().getSpecificCoinDetailInfo(fSym)
    }

    init {
        loadData()
    }

    private fun loadData() {
        val disposable = ApiFactory.apiService.getTopCoins()
            .map { it.topCoinsList?.map { it.coinInfo?.name }?.joinToString(",") }
            .flatMap { ApiFactory.apiService.getFullDataOfCoins(coinName = it) }
            .map { getListOfCoinPriceInfo(it) }
            .subscribeOn(Schedulers.io())
            .subscribe({
                database.getDao().addCoinPriceDataObject(it)
                Log.d("process_result", " message1: $it")
            }, {
                Log.e("process_result", "Error message: ${it.message}")
                throw Exception()
            })

        compositeDisposable.add(disposable)
    }

    private fun getListOfCoinPriceInfo(coinPriceInfoRawData: CoinPriceInfoRawData): List<CoinPriceInfo> {
        val result = ArrayList<CoinPriceInfo>()
        val jsonObject = coinPriceInfoRawData.coinListRawData ?: return result
        val coinsKeySet = jsonObject.keySet()
        for (coinKey in coinsKeySet) {
            val currencyJson = jsonObject.getAsJsonObject(coinKey)
            val currencyKeySet = currencyJson.keySet()
            for (currencyKey in currencyKeySet) {
                val coinPriceInfo: CoinPriceInfo = Gson().fromJson(
                    currencyJson.getAsJsonObject(currencyKey),
                    CoinPriceInfo::class.java
                )
                result.add(coinPriceInfo)
            }
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }
}