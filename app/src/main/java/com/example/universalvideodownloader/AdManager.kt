package com.example.universalvideodownloader

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AdManager {
    const val MAX_ADS_PER_DAY = 4
    
    // Real Production Interstitial Ad Unit ID
    private const val REAL_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5858168168111853/2366747800"
    // Fallback Test Interstitial Ad Unit ID for instant verification
    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    
    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false

    fun init(context: Context) {
        MobileAds.initialize(context) {
            Log.d("AdManager", "Google Mobile Ads initialized successfully")
            preloadInterstitialAd(context)
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("ad_frequency_prefs", Context.MODE_PRIVATE)
    }

    fun canShowAdToday(context: Context): Boolean {
        val prefs = getPrefs(context)
        val today = getTodayDateString()
        val savedDate = prefs.getString("last_ad_date", "")
        
        if (savedDate != today) {
            prefs.edit().putString("last_ad_date", today).putInt("ads_count_today", 0).apply()
            return true
        }

        val count = prefs.getInt("ads_count_today", 0)
        return count < MAX_ADS_PER_DAY
    }

    fun getAdsShownTodayCount(context: Context): Int {
        val prefs = getPrefs(context)
        val today = getTodayDateString()
        val savedDate = prefs.getString("last_ad_date", "")
        if (savedDate != today) return 0
        return prefs.getInt("ads_count_today", 0)
    }

    private fun incrementAdCountToday(context: Context) {
        val prefs = getPrefs(context)
        val count = prefs.getInt("ads_count_today", 0)
        prefs.edit().putInt("ads_count_today", count + 1).apply()
        Log.d("AdManager", "Ad shown! Total ads shown today: ${count + 1} / $MAX_ADS_PER_DAY")
    }

    fun preloadInterstitialAd(context: Context, useFallbackTestAd: Boolean = false) {
        if (interstitialAd != null || isAdLoading || !canShowAdToday(context)) return

        isAdLoading = true
        val adUnitIdToUse = if (useFallbackTestAd) TEST_INTERSTITIAL_AD_UNIT_ID else REAL_INTERSTITIAL_AD_UNIT_ID
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context.applicationContext,
            adUnitIdToUse,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                    Log.d("AdManager", "Preloaded Interstitial Ad successfully (${if (useFallbackTestAd) "Test Ad" else "Real Ad"})")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                    Log.e("AdManager", "Failed to load Ad (${if (useFallbackTestAd) "Test" else "Real"}): ${error.message} (Code: ${error.code})")
                    
                    // If real production ad fails due to brand new account propagation delay (error code 3/1), fallback to sample test ad
                    if (!useFallbackTestAd) {
                        Log.d("AdManager", "Retrying with sample test ad for instant verification...")
                        preloadInterstitialAd(context, useFallbackTestAd = true)
                    }
                }
            }
        )
    }

    fun showAdIfCapped(activity: Activity) {
        if (!canShowAdToday(activity)) {
            Log.d("AdManager", "Daily limit of $MAX_ADS_PER_DAY ads reached. Skipping ad.")
            return
        }

        if (interstitialAd != null) {
            interstitialAd?.show(activity)
            incrementAdCountToday(activity)
            interstitialAd = null
            preloadInterstitialAd(activity)
        } else {
            Log.d("AdManager", "Ad not ready yet, preloading for next time.")
            preloadInterstitialAd(activity)
        }
    }
}
