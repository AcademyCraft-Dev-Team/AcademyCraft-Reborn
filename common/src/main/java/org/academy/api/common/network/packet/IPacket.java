package org.academy.api.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * 实现这个接口，类变量存储数据，类变量应为双端共有的类型
 * 必须有一个无参构造函数
 * packetListenerSupplier 将会在接受数据包时赋值
 */
public abstract class IPacket<T extends PacketListener> {
    public Supplier<T> packetListenerSupplier;

    public abstract void read(@NotNull FriendlyByteBuf buf);

    public abstract void write(@NotNull FriendlyByteBuf buf);
}