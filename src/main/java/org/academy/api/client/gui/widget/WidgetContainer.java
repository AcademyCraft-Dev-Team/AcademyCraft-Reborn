package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface WidgetContainer extends Widget {
    boolean isLayoutDirty();

    void addChild(String name, Widget child);

    void removeChild(String name);

    void clearChildren();

    Map<String, Widget> getChildren();

    @Nullable
    Widget getHoveredWidget();

    void setFocusedChild(@Nullable Widget widget);

    LayoutParams generateDefaultLayoutParams();

    LayoutParams generateLayoutParams(LayoutParams p);

    boolean checkLayoutParams(LayoutParams p);

    class LayoutParams {
        /**
         * 代表 null 喵
         */
        public static final LayoutParams NONE = new LayoutParams();

        public SizeMode widthMode = SizeMode.WRAP_CONTENT;
        public SizeMode heightMode = SizeMode.WRAP_CONTENT;
        public int gravity = Gravity.TOP_LEFT;

        public float width = 0;
        public float height = 0;

        public float marginLeft = 0;
        public float marginTop = 0;
        public float marginRight = 0;
        public float marginBottom = 0;

        public float paddingLeft = 0;
        public float paddingTop = 0;
        public float paddingRight = 0;
        public float paddingBottom = 0;

        public LayoutParams() {
        }

        public LayoutParams(LayoutParams source) {
            widthMode = source.widthMode;
            heightMode = source.heightMode;
            gravity = source.gravity;
            width = source.width;
            height = source.height;
            marginLeft = source.marginLeft;
            marginTop = source.marginTop;
            marginRight = source.marginRight;
            marginBottom = source.marginBottom;
            paddingLeft = source.paddingLeft;
            paddingTop = source.paddingTop;
            paddingRight = source.paddingRight;
            paddingBottom = source.paddingBottom;
        }

        public LayoutParams widthMode(SizeMode mode) {
            widthMode = mode;
            return this;
        }

        public LayoutParams heightMode(SizeMode mode) {
            heightMode = mode;
            return this;
        }

        public LayoutParams sizeMode(SizeMode mode) {
            widthMode = mode;
            heightMode = mode;
            return this;
        }

        public LayoutParams sizeMode(SizeMode widthMode, SizeMode heightMode) {
            this.widthMode = widthMode;
            this.heightMode = heightMode;
            return this;
        }

        public LayoutParams size(float width, float height) {
            widthMode = SizeMode.FIXED;
            heightMode = SizeMode.FIXED;
            this.width = width;
            this.height = height;
            return this;
        }


        public LayoutParams width(float width) {
            widthMode = SizeMode.FIXED;
            this.width = width;
            return this;
        }

        public LayoutParams height(float height) {
            heightMode = SizeMode.FIXED;
            this.height = height;
            return this;
        }

        public LayoutParams gravity(int gravity) {
            this.gravity = gravity;
            return this;
        }

        public LayoutParams margin(float all) {
            marginLeft = all;
            marginTop = all;
            marginRight = all;
            marginBottom = all;
            return this;
        }

        public LayoutParams margin(float vertical, float horizontal) {
            marginTop = vertical;
            marginBottom = vertical;
            marginLeft = horizontal;
            marginRight = horizontal;
            return this;
        }

        public LayoutParams margin(float left, float top, float right, float bottom) {
            marginLeft = left;
            marginTop = top;
            marginRight = right;
            marginBottom = bottom;
            return this;
        }

        public LayoutParams marginLeft(float left) {
            marginLeft = left;
            return this;
        }

        public LayoutParams marginTop(float top) {
            marginTop = top;
            return this;
        }

        public LayoutParams marginRight(float right) {
            marginRight = right;
            return this;
        }

        public LayoutParams marginBottom(float bottom) {
            marginBottom = bottom;
            return this;
        }

        public LayoutParams marginHorizontal(float horizontal) {
            marginLeft = horizontal;
            marginRight = horizontal;
            return this;
        }

        public LayoutParams marginVertical(float vertical) {
            marginTop = vertical;
            marginBottom = vertical;
            return this;
        }

        public LayoutParams padding(float all) {
            paddingLeft = all;
            paddingTop = all;
            paddingRight = all;
            paddingBottom = all;
            return this;
        }

        public LayoutParams padding(float horizontal, float vertical) {
            paddingLeft = horizontal;
            paddingRight = horizontal;
            paddingTop = vertical;
            paddingBottom = vertical;
            return this;
        }

        public LayoutParams padding(float left, float top, float right, float bottom) {
            paddingLeft = left;
            paddingTop = top;
            paddingRight = right;
            paddingBottom = bottom;
            return this;
        }

        public LayoutParams paddingLeft(float left) {
            paddingLeft = left;
            return this;
        }

        public LayoutParams paddingTop(float top) {
            paddingTop = top;
            return this;
        }

        public LayoutParams paddingRight(float right) {
            paddingRight = right;
            return this;
        }

        public LayoutParams paddingBottom(float bottom) {
            paddingBottom = bottom;
            return this;
        }

        public LayoutParams paddingHorizontal(float horizontal) {
            paddingLeft = horizontal;
            paddingRight = horizontal;
            return this;
        }

        public LayoutParams paddingVertical(float vertical) {
            paddingTop = vertical;
            paddingBottom = vertical;
            return this;
        }
    }
}