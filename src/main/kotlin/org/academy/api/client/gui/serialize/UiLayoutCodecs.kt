package org.academy.api.client.gui.serialize

import org.academy.api.client.gui.serialize.codecs.BlendQuadCodec
import org.academy.api.client.gui.serialize.codecs.ButtonCodec
import org.academy.api.client.gui.serialize.codecs.EmptyCodec
import org.academy.api.client.gui.serialize.codecs.FillCodec
import org.academy.api.client.gui.serialize.codecs.FrameLayoutCodec
import org.academy.api.client.gui.serialize.codecs.ImageCodec
import org.academy.api.client.gui.serialize.codecs.LabelCodec
import org.academy.api.client.gui.serialize.codecs.LinearLayoutCodec
import org.academy.api.client.gui.serialize.codecs.MonochromeImageCodec
import org.academy.api.client.gui.serialize.codecs.ParallaxImageCodec
import org.academy.api.client.gui.serialize.codecs.ProgressBarCodec
import org.academy.api.client.gui.serialize.codecs.RadioButtonCodec
import org.academy.api.client.gui.serialize.codecs.RadioGroupCodec
import org.academy.api.client.gui.serialize.codecs.ScrollBarCodec
import org.academy.api.client.gui.serialize.codecs.ScrollPanelCodec
import org.academy.api.client.gui.serialize.codecs.SeekBarCodec
import org.academy.api.client.gui.serialize.codecs.SpriteSheetCodec
import org.academy.api.client.gui.serialize.codecs.TextBoxCodec
import org.academy.api.client.gui.serialize.codecs.ToggleButtonCodec
import org.academy.api.client.gui.serialize.codecs.WheelPickerCodec

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
