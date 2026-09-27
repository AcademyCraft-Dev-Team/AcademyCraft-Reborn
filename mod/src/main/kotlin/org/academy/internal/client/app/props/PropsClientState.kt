package org.academy.internal.client.app.props

import org.academy.api.common.attribute.AbilityFactor
import org.academy.internal.common.attribute.PropsMath
import org.academy.internal.common.attribute.PropsPackets
import org.misaka.MisakaNetworkClient
import org.misaka.api.common.network.annotation.SubscribePacket

object PropsClientState {
    private val VALUES = DoubleArray(AbilityFactor.entries.size)
    private var lockedMask = 0
    private var started = false

    fun init() {
        // register(Class) 只会注册静态方法，object 的 @SubscribePacket 是实例方法，必须传实例喵
        MisakaNetworkClient.NETWORK_MANAGER.register(PropsClientState)
    }

    @SubscribePacket
    fun sync(packet: PropsPackets.SyncPacket) {
        val incoming = packet.values()
        System.arraycopy(incoming, 0, VALUES, 0, minOf(incoming.size, VALUES.size))
        lockedMask = packet.lockedMask()
        started = packet.started()
    }

    fun get(factor: AbilityFactor): Double = VALUES[factor.ordinal]

    fun total(): Double = VALUES.sum()

    fun coefficient(): Double = PropsMath.acquisitionCoefficient(total())

    fun isLocked(factor: AbilityFactor): Boolean = (lockedMask and factor.bit()) != 0

    internal fun setLockedLocally(factor: AbilityFactor, locked: Boolean) {
        if (locked) {
            lockedMask = lockedMask or factor.bit()
        } else {
            lockedMask = lockedMask and factor.bit().inv()
        }
    }

    fun isStarted(): Boolean = started
}
