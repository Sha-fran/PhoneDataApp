package com.example.phonedataapp

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.phonedataapp.ui.theme.PhoneDataAppTheme
import android.provider.Settings
import android.telephony.TelephonyManager
import java.util.Calendar

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        } else {
            showAppData()
        }
    }

    private fun showAppData() {
        setContent {
            PhoneDataAppTheme {
                GetSomeData(this)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onResume() {
        super.onResume()
        if (hasUsageStatsPermission()) {
            showAppData()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    private fun requestUsageStatsPermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}


@Composable
fun GetSomeData(context: Context) {
    val appInfoList = remember { getAppInfoList(context) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        for (appInfo in appInfoList) {
            Text(text = appInfo, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "---------------", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

fun getAppInfoList(context: Context): List<String> {
    val packageManager = context.packageManager
    val packages: List<ApplicationInfo> =
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    val appInfoList: MutableList<String> = mutableListOf()
    for (packageInfo: ApplicationInfo in packages) {
        val appName: String = packageManager.getApplicationLabel(packageInfo).toString()
        val packageName: String = packageInfo.packageName
        val versionName: String = packageManager.getPackageInfo(packageName, 0).versionName ?: "N/A"
        val (wifiBytes, mobileBytes) = getNetworkStats(context, packageName)
        appInfoList.add("App Name: $appName\n" +
                "Package Name: $packageName\n" +
                "Version: $versionName\n" +
                "Wi-Fi Usage: ${formatBytes(wifiBytes)}\n" +
                "Mobile Usage: ${formatBytes(mobileBytes)}")
    }
    return appInfoList
}

fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return String.format("%.2f %s", value, units[unitIndex])
}

fun getNetworkStats(context: Context, packageName: String): Pair<Long, Long> {
    val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    val calendar = Calendar.getInstance()
    val endTime = calendar.timeInMillis
    calendar.add(Calendar.MONTH, -1)
    val startTime = calendar.timeInMillis

    var wifiBytes = 0L
    var mobileBytes = 0L

    try {
        val uid = context.packageManager.getApplicationInfo(packageName, 0).uid

        // Wi-Fi stats
        val wifiStats = networkStatsManager.queryDetailsForUid(
            ConnectivityManager.TYPE_WIFI,
            null,
            startTime,
            endTime,
            uid
        )
        var bucket = NetworkStats.Bucket()
        while (wifiStats.hasNextBucket()) {
            wifiStats.getNextBucket(bucket)
            wifiBytes += bucket.rxBytes + bucket.txBytes
        }
        wifiStats.close()

        // Mobile stats
        val mobileStats = networkStatsManager.queryDetailsForUid(
            ConnectivityManager.TYPE_MOBILE,
            null,
            startTime,
            endTime,
            uid
        )
        while (mobileStats.hasNextBucket()) {
            mobileStats.getNextBucket(bucket)
            mobileBytes += bucket.rxBytes + bucket.txBytes
        }
        mobileStats.close()
    } catch (e: RemoteException) {
        e.printStackTrace()
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    } catch (e: SecurityException) {
        e.printStackTrace()
    }

    return Pair(wifiBytes, mobileBytes)
}

fun getSubscriberId(context: Context): String {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    return tm.subscriberId ?: ""
}
