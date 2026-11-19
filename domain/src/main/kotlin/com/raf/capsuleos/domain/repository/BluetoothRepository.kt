package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for observing connected Bluetooth peripherals and their reported battery levels.
 */
interface BluetoothRepository {
    /** Emits Bluetooth connection and battery update events mapped to [CapsuleEvent]. */
    fun observeBluetoothEvents(): Flow<CapsuleEvent>

    /** Returns the most recently connected Bluetooth peripheral snapshot if available and permitted. */
    fun currentConnectedDevice(): CapsuleContent.BluetoothDevice?

    /** True when Android `BLUETOOTH_CONNECT` runtime permission is granted. */
    fun hasBluetoothPermission(): Boolean
}
