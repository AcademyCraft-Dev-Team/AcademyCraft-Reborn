package org.academy.api.client.gui.serialize

import org.academy.api.client.gui.serialize.codecs.*

/**
 * 注册全部内置控件 codec. 懒加载, 首次序列化/反序列化时执行.
 */
object UiLayoutCodecs {
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            WidgetCodecRegistry.register(LabelCodec())
            WidgetCodecRegistry.register(TextBoxCodec())
            WidgetCodecRegistry.register(ImageCodec())
            WidgetCodecRegistry.register(MonochromeImageCodec())
            WidgetCodecRegistry.register(ParallaxImageCodec())
            WidgetCodecRegistry.register(FillCodec())
            WidgetCodecRegistry.register(EmptyCodec())
            WidgetCodecRegistry.register(ButtonCodec())
            WidgetCodecRegistry.register(LinearLayoutCodec())
            WidgetCodecRegistry.register(FrameLayoutCodec())
            WidgetCodecRegistry.register(ScrollPanelCodec())
            WidgetCodecRegistry.register(WheelPickerCodec())
            WidgetCodecRegistry.register(RadioGroupCodec())
            WidgetCodecRegistry.register(ProgressBarCodec())
            WidgetCodecRegistry.register(SeekBarCodec())
            WidgetCodecRegistry.register(ToggleButtonCodec())
            WidgetCodecRegistry.register(RadioButtonCodec())
            WidgetCodecRegistry.register(ScrollBarCodec())
            WidgetCodecRegistry.register(SpriteSheetCodec())
            WidgetCodecRegistry.register(BlendQuadCodec())
            registered = true
        }
    }
}
