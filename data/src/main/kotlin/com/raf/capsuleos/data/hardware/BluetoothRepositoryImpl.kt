package com.raf.capsuleos.data.hardware

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.raf.capsuleos.domain.model.BluetoothDeviceCategory
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.repository.BluetoothRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes paired/connected Bluetooth audio & accessory devices cleanly behind Android 12+
 * `BLUETOOTH_CONNECT` runtime permission checks.
 */
@Singleton
class BluetoothRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BluetoothRepository {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    private var lastBatteryByAddress = mutableMapOf<String, Int>()
    private var lastConnectedDevice: CapsuleContent.BluetoothDevice? = null

    override fun hasBluetoothPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED

    override fun observeBluetoothEvents(): Flow<CapsuleEvent> = callbackFlow {
        if (!hasBluetoothPermission()) {
            trySend(CapsuleEvent.BluetoothDeviceUpdated(null))
            awaitClose {}
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (!hasBluetoothPermission()) {
                    trySend(CapsuleEvent.BluetoothDeviceUpdated(null))
                    return
                }
                val device = extractBluetoothDeviceExtra(intent)
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        if (device != null) {
                            val mapped = toDomainDevice(device, batteryOverride = lastBatteryByAddress[device.address], isConnected = true)
                            lastConnectedDevice = mapped
                            trySend(CapsuleEvent.BluetoothDeviceUpdated(mapped))
                        }
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        if (device != null && lastConnectedDevice?.address == device.address) {
                            lastConnectedDevice = null
                            trySend(CapsuleEvent.BluetoothDeviceUpdated(null))
                        } else {
                            trySend(CapsuleEvent.BluetoothDeviceUpdated(currentConnectedDevice()))
                        }
                    }
                    ACTION_BATTERY_LEVEL_CHANGED -> {
                        val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                        if (device != null && level in 0..100) {
                            lastBatteryByAddress[device.address] = level
                            val mapped = toDomainDevice(device, batteryOverride = level, isConnected = true)
                            if (lastConnectedDevice?.address == device.address || lastConnectedDevice == null) {
                                lastConnectedDevice = mapped
                            }
                            trySend(CapsuleEvent.BluetoothDeviceUpdated(mapped))
                        }
                    }
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                    -> {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                        if (state == BluetoothProfile.STATE_CONNECTED && device != null) {
                            val mapped = toDomainDevice(device, batteryOverride = lastBatteryByAddress[device.address], isConnected = true)
                            lastConnectedDevice = mapped
                            trySend(CapsuleEvent.BluetoothDeviceUpdated(mapped))
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            val current = currentConnectedDevice()
                            lastConnectedDevice = current
                            trySend(CapsuleEvent.BluetoothDeviceUpdated(current))
                        }
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                        if (adapterState != BluetoothAdapter.STATE_ON) {
                            lastConnectedDevice = null
                            trySend(CapsuleEvent.BluetoothDeviceUpdated(null))
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        currentConnectedDevice()?.let { initial ->
            lastConnectedDevice = initial
            trySend(CapsuleEvent.BluetoothDeviceUpdated(initial))
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    override fun currentConnectedDevice(): CapsuleContent.BluetoothDevice? {
        if (!hasBluetoothPermission()) return null
        val adapter = bluetoothManager?.adapter ?: return null
        if (!adapter.isEnabled) return null
        val chosen = try {
            adapter.bondedDevices.firstOrNull { isDeviceAclConnected(it) }
                ?: bluetoothManager.getConnectedDevices(BluetoothProfile.GATT).firstOrNull()
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return lastConnectedDevice
        return toDomainDevice(chosen, batteryOverride = lastBatteryByAddress[chosen.address], isConnected = true)
    }

    private fun toDomainDevice(
        device: BluetoothDevice,
        batteryOverride: Int?,
        isConnected: Boolean,
    ): CapsuleContent.BluetoothDevice {
        val safeName = try {
            device.alias ?: device.name
        } catch (_: SecurityException) {
            null
        }?.takeIf { it.isNotBlank() } ?: device.address
        val btClass = try {
            device.bluetoothClass
        } catch (_: SecurityException) {
            null
        }
        val category = mapBluetoothCategory(btClass)
        val battery = batteryOverride ?: runCatching { readBatteryLevelReflection(device) }.getOrNull()
        return CapsuleContent.BluetoothDevice(
            name = safeName,
            address = device.address,
            batteryPercent = battery?.takeIf { it in 0..100 },
            category = category,
            isConnected = isConnected,
        )
    }

    private fun extractBluetoothDeviceExtra(intent: Intent): BluetoothDevice? =
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)

    private fun isDeviceAclConnected(device: BluetoothDevice): Boolean =
        runCatching {
            val method = BluetoothDevice::class.java.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        }.getOrDefault(false)

    private fun readBatteryLevelReflection(device: BluetoothDevice): Int? =
        runCatching {
            val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
            (method.invoke(device) as? Int)?.takeIf { it in 0..100 }
        }.getOrNull()

    private fun mapBluetoothCategory(btClass: BluetoothClass?): BluetoothDeviceCategory {
        val deviceClass = btClass?.deviceClass ?: return BluetoothDeviceCategory.OTHER
        val majorClass = btClass.majorDeviceClass
        return when (deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            -> BluetoothDeviceCategory.HEADPHONES
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
            -> BluetoothDeviceCategory.SPEAKER
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> BluetoothDeviceCategory.CAR
            BluetoothClass.Device.WEARABLE_WRIST_WATCH -> BluetoothDeviceCategory.WATCH
            else -> when (majorClass) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> BluetoothDeviceCategory.HEADPHONES
                BluetoothClass.Device.Major.WEARABLE -> BluetoothDeviceCategory.WATCH
                BluetoothClass.Device.Major.COMPUTER -> BluetoothDeviceCategory.COMPUTER
                BluetoothClass.Device.Major.PHONE -> BluetoothDeviceCategory.PHONE
                else -> BluetoothDeviceCategory.OTHER
            }
        }
    }

    private companion object {
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}
