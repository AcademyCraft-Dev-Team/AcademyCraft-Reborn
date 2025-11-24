package org.academy.api.client.gui.widget;

import org.academy.AcademyCraft;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.Orientation;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.*;

import static org.academy.api.client.gui.layout.Gravity.*;

public class GridLayoutWidget extends AbstractWidgetContainer {
    public static final int UNDEFINED = Integer.MIN_VALUE;
    public static final int ALIGN_BOUNDS = 0;
    public static final int ALIGN_MARGINS = 1;

    private static final int MAX_SIZE = 100000;
    private static final int UNINITIALIZED_HASH = 0;

    private static final Orientation DEFAULT_ORIENTATION = Orientation.HORIZONTAL;
    private static final int DEFAULT_COUNT = UNDEFINED;
    private static final int DEFAULT_ALIGNMENT_MODE = ALIGN_MARGINS;

    private final Axis horizontalAxis = new Axis(true);
    private final Axis verticalAxis = new Axis(false);
    private Orientation orientation = DEFAULT_ORIENTATION;
    private int alignmentMode = DEFAULT_ALIGNMENT_MODE;
    private int lastLayoutParamsHashCode = UNINITIALIZED_HASH;

    public GridLayoutWidget() {
        setRowCount(DEFAULT_COUNT);
        setColumnCount(DEFAULT_COUNT);
        setOrientation(DEFAULT_ORIENTATION);
        setAlignmentMode(DEFAULT_ALIGNMENT_MODE);
        setRowOrderPreserved(true);
        setColumnOrderPreserved(true);
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public void setOrientation(Orientation orientation) {
        if (this.orientation != orientation) {
            this.orientation = orientation;
            invalidateStructure();
            requestLayout();
        }
    }

    public int getRowCount() {
        return verticalAxis.getCount();
    }

    public void setRowCount(int rowCount) {
        verticalAxis.setCount(rowCount);
        invalidateStructure();
        requestLayout();
    }

    public int getColumnCount() {
        return horizontalAxis.getCount();
    }

    public void setColumnCount(int columnCount) {
        horizontalAxis.setCount(columnCount);
        invalidateStructure();
        requestLayout();
    }

    public int getAlignmentMode() {
        return alignmentMode;
    }

    public void setAlignmentMode(int alignmentMode) {
        this.alignmentMode = alignmentMode;
        requestLayout();
    }

    public boolean isRowOrderPreserved() {
        return verticalAxis.isOrderPreserved();
    }

    public void setRowOrderPreserved(boolean rowOrderPreserved) {
        verticalAxis.setOrderPreserved(rowOrderPreserved);
        invalidateStructure();
        requestLayout();
    }

    public boolean isColumnOrderPreserved() {
        return horizontalAxis.isOrderPreserved();
    }

    public void setColumnOrderPreserved(boolean columnOrderPreserved) {
        horizontalAxis.setOrderPreserved(columnOrderPreserved);
        invalidateStructure();
        requestLayout();
    }

    public static Spec spec(int start, int size, Alignment alignment, float weight) {
        return new Spec(start != UNDEFINED, start, size, alignment, weight);
    }

    public static Spec spec(int start, Alignment alignment, float weight) {
        return spec(start, 1, alignment, weight);
    }

    public static Spec spec(int start, int size, float weight) {
        return spec(start, size, UNDEFINED_ALIGNMENT, weight);
    }

    public static Spec spec(int start, float weight) {
        return spec(start, 1, weight);
    }

    public static Spec spec(int start, int size, Alignment alignment) {
        return spec(start, size, alignment, Spec.DEFAULT_WEIGHT);
    }

    public static Spec spec(int start, Alignment alignment) {
        return spec(start, 1, alignment);
    }

    public static Spec spec(int start, int size) {
        return spec(start, size, UNDEFINED_ALIGNMENT);
    }

    public static Spec spec(int start) {
        return spec(start, 1);
    }

    private static int max2(int[] a, int valueIfEmpty) {
        var result = valueIfEmpty;
        for (var j : a) {
            result = Math.max(result, j);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] append(T[] a, T[] b) {
        var result = (T[]) Array.newInstance(a.getClass().getComponentType(), a.length + b.length);
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static Alignment getAlignment(int gravity, boolean horizontal) {
        var mask = horizontal ? HORIZONTAL_GRAVITY_MASK : VERTICAL_GRAVITY_MASK;
        var shift = horizontal ? AXIS_X_SHIFT : AXIS_Y_SHIFT;
        var flags = (gravity & mask) >> shift;
        return switch (flags) {
            case (AXIS_SPECIFIED | AXIS_PULL_BEFORE) -> horizontal ? LEFT : TOP;
            case (AXIS_SPECIFIED | AXIS_PULL_AFTER) -> horizontal ? RIGHT : BOTTOM;
            case (AXIS_SPECIFIED | AXIS_PULL_BEFORE | AXIS_PULL_AFTER) -> FILL;
            case AXIS_SPECIFIED -> CENTER;
            default -> UNDEFINED_ALIGNMENT;
        };
    }

    private int getMargin(Widget view, boolean horizontal, boolean leading) {
        if (alignmentMode == ALIGN_MARGINS) {
            var lp = getLayoutParams(view);
            return (int) (horizontal ? (leading ? lp.marginLeft : lp.marginRight) : (leading ? lp.marginTop : lp.marginBottom));
        } else {
            var axis = horizontal ? horizontalAxis : verticalAxis;
            var margins = leading ? axis.getLeadingMargins() : axis.getTrailingMargins();
            if (margins == null) return 0;
            var lp = getLayoutParams(view);
            var spec = horizontal ? lp.columnSpec : lp.rowSpec;
            var index = leading ? spec.span.min : spec.span.max;
            return margins[index];
        }
    }

    private int getTotalMargin(Widget child, boolean horizontal) {
        return getMargin(child, horizontal, true) + getMargin(child, horizontal, false);
    }

    private static boolean fits(int[] a, int value, int start, int end) {
        if (end > a.length) return false;
        for (var i = start; i < end; i++) {
            if (a[i] > value) return false;
        }
        return true;
    }

    private static void procrusteanFill(int[] a, int start, int end, int value) {
        var length = a.length;
        Arrays.fill(a, Math.min(start, length), Math.min(end, length), value);
    }

    private static void setCellGroup(LayoutParams lp, int row, int rowSpan, int col, int colSpan) {
        lp.setRowSpecSpan(new Interval(row, row + rowSpan));
        lp.setColumnSpecSpan(new Interval(col, col + colSpan));
    }

    private static int clip(Interval minorRange, boolean minorWasDefined, int count) {
        var size = minorRange.size();
        if (count == 0) return size;
        var min = minorWasDefined ? Math.min(minorRange.min, count) : 0;
        return Math.min(size, count - min);
    }

    private void validateLayoutParams() {
        var horizontal = (orientation == Orientation.HORIZONTAL);
        var axis = horizontal ? horizontalAxis : verticalAxis;
        var count = (axis.definedCount != UNDEFINED) ? axis.definedCount : 0;

        var major = 0;
        var minor = 0;
        var maxSizes = new int[count];
        var childList = children.values().stream().toList();

        for (var child : childList) {
            var lp = (LayoutParams) child.getLayoutParams();

            var majorSpec = horizontal ? lp.rowSpec : lp.columnSpec;
            var majorRange = majorSpec.span;
            var majorWasDefined = majorSpec.startDefined;
            var majorSpan = majorRange.size();
            if (majorWasDefined) major = majorRange.min;

            var minorSpec = horizontal ? lp.columnSpec : lp.rowSpec;
            var minorRange = minorSpec.span;
            var minorWasDefined = minorSpec.startDefined;
            var minorSpan = clip(minorRange, minorWasDefined, count);
            if (minorWasDefined) minor = minorRange.min;

            if (count != 0) {
                if (!majorWasDefined || !minorWasDefined) {
                    while (!fits(maxSizes, major, minor, minor + minorSpan)) {
                        if (minorWasDefined) {
                            major++;
                        } else {
                            if (minor + minorSpan <= count) {
                                minor++;
                            } else {
                                minor = 0;
                                major++;
                            }
                        }
                    }
                }
                procrusteanFill(maxSizes, minor, minor + minorSpan, major + majorSpan);
            }

            if (horizontal) setCellGroup(lp, major, majorSpan, minor, minorSpan);
            else setCellGroup(lp, minor, minorSpan, major, majorSpan);

            minor = minor + minorSpan;
        }
    }

    private void invalidateStructure() {
        lastLayoutParamsHashCode = UNINITIALIZED_HASH;
        horizontalAxis.invalidateStructure();
        verticalAxis.invalidateStructure();
        invalidateValues();
    }

    private void invalidateValues() {
        horizontalAxis.invalidateValues();
        verticalAxis.invalidateValues();
    }

    private LayoutParams getLayoutParams(Widget c) {
        return (LayoutParams) c.getLayoutParams();
    }

    private static void handleInvalidParams(String msg) {
        throw new IllegalArgumentException(msg + ". ");
    }

    private void checkLayoutParams(LayoutParams lp, boolean horizontal) {
        var groupName = horizontal ? "column" : "row";
        var spec = horizontal ? lp.columnSpec : lp.rowSpec;
        var span = spec.span;
        if (span.min != UNDEFINED && span.min < 0) {
            handleInvalidParams(groupName + " indices must be positive");
        }
        var axis = horizontal ? horizontalAxis : verticalAxis;
        var count = axis.definedCount;
        if (count != UNDEFINED) {
            if (span.max > count) {
                handleInvalidParams(groupName + " indices (start + span) mustn't exceed the " + groupName + " count");
            }
            if (span.size() > count) {
                handleInvalidParams(groupName + " span mustn't exceed the " + groupName + " count");
            }
        }
    }

    @Override
    public boolean checkLayoutParams(WidgetContainer.LayoutParams p) {
        if (!(p instanceof LayoutParams lp)) return false;
        checkLayoutParams(lp, true);
        checkLayoutParams(lp, false);
        return true;
    }

    @Override
    public LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    public LayoutParams generateLayoutParams(WidgetContainer.LayoutParams lp) {
        if (lp instanceof LayoutParams) return new LayoutParams((LayoutParams) lp);
        return new LayoutParams(lp);
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        invalidateStructure();
    }

    private int computeLayoutParamsHashCode() {
        var result = 1;
        for (var c : getChildren().values()) {
            if (!c.isVisible()) continue;
            var lp = (LayoutParams) c.getLayoutParams();
            result = 31 * result + lp.hashCode();
        }
        return result;
    }

    private void consistencyCheck() {
        if (lastLayoutParamsHashCode == UNINITIALIZED_HASH) {
            validateLayoutParams();
            lastLayoutParamsHashCode = computeLayoutParamsHashCode();
        } else if (lastLayoutParamsHashCode != computeLayoutParamsHashCode()) {
            AcademyCraft.LOGGER.warn("GridLayout layout parameters were modified between layout operations. This may lead to unexpected results.");
            invalidateStructure();
            consistencyCheck();
        }
    }

    private void measureChildWithMargins2(Widget child, MeasureSpec parentWidthSpec, MeasureSpec parentHeightSpec) {
        var lp = getLayoutParams(child);
        var childWidthSpec = getChildMeasureSpec(parentWidthSpec, getTotalMargin(child, true), lp.width, lp.widthMode);
        var childHeightSpec = getChildMeasureSpec(parentHeightSpec, getTotalMargin(child, false), lp.height, lp.heightMode);
        child.measure(childWidthSpec, childHeightSpec);
    }

    private void measureChildrenWithMargins(MeasureSpec widthSpec, MeasureSpec heightSpec, boolean firstPass) {
        for (var c : getChildren().values()) {
            if (!c.isVisible()) continue;
            if (firstPass) {
                measureChildWithMargins2(c, widthSpec, heightSpec);
            } else {
                var lp = getLayoutParams(c);
                var horizontal = (orientation == Orientation.HORIZONTAL);
                var spec = horizontal ? lp.columnSpec : lp.rowSpec;
                if (spec.getAbsoluteAlignment(horizontal) == FILL) {
                    var span = spec.span;
                    var axis = horizontal ? horizontalAxis : verticalAxis;
                    var locations = axis.getLocations();
                    if (locations == null) continue;
                    var cellSize = locations[span.max] - locations[span.min];
                    var viewSize = cellSize - getTotalMargin(c, horizontal);
                    if (horizontal) {
                        measureChildWithMargins2(c, new MeasureSpec(MeasureSpec.Mode.EXACTLY, viewSize), heightSpec);
                    } else {
                        measureChildWithMargins2(c, widthSpec, new MeasureSpec(MeasureSpec.Mode.EXACTLY, viewSize));
                    }
                }
            }
        }
    }

    private static MeasureSpec adjust(MeasureSpec measureSpec, int delta) {
        return new MeasureSpec(measureSpec.getMode(), measureSpec.getSize() + delta);
    }

    @Override
    protected void onMeasure(MeasureSpec widthSpec, MeasureSpec heightSpec) {
        consistencyCheck();
        invalidateValues();

        var hPadding = getLayoutParams().paddingLeft + getLayoutParams().paddingRight;
        var vPadding = getLayoutParams().paddingTop + getLayoutParams().paddingBottom;

        var widthSpecSansPadding = adjust(widthSpec, (int) -hPadding);
        var heightSpecSansPadding = adjust(heightSpec, (int) -vPadding);

        measureChildrenWithMargins(widthSpecSansPadding, heightSpecSansPadding, true);

        float widthSansPadding;
        float heightSansPadding;

        if (orientation == Orientation.HORIZONTAL) {
            widthSansPadding = horizontalAxis.getMeasure(widthSpecSansPadding);
            measureChildrenWithMargins(widthSpecSansPadding, heightSpecSansPadding, false);
            heightSansPadding = verticalAxis.getMeasure(heightSpecSansPadding);
        } else {
            heightSansPadding = verticalAxis.getMeasure(heightSpecSansPadding);
            measureChildrenWithMargins(widthSpecSansPadding, heightSpecSansPadding, false);
            widthSansPadding = horizontalAxis.getMeasure(widthSpecSansPadding);
        }

        var measuredWidth = Math.max(widthSansPadding + hPadding, 0);
        var measuredHeight = Math.max(heightSansPadding + vPadding, 0);

        setMeasuredDimension(resolveSize(measuredWidth, widthSpec), resolveSize(measuredHeight, heightSpec));
    }

    private float getMeasurement(Widget c, boolean horizontal) {
        return horizontal ? c.getMeasuredWidth() : c.getMeasuredHeight();
    }

    private float getMeasurementIncludingMargin(Widget c, boolean horizontal) {
        if (!c.isVisible()) return 0;
        return getMeasurement(c, horizontal) + getTotalMargin(c, horizontal);
    }

    @Override
    protected void onLayout() {
        consistencyCheck();

        var targetWidth = getWidth();
        var targetHeight = getHeight();

        var lp = getLayoutParams();
        var paddingLeft = lp.paddingLeft;
        var paddingTop = lp.paddingTop;
        var paddingRight = lp.paddingRight;
        var paddingBottom = lp.paddingBottom;

        horizontalAxis.layout((int) (targetWidth - paddingLeft - paddingRight));
        verticalAxis.layout((int) (targetHeight - paddingTop - paddingBottom));

        var hLocations = horizontalAxis.getLocations();
        var vLocations = verticalAxis.getLocations();
        if (hLocations == null || vLocations == null) return;

        var childList = new ArrayList<>(getChildren().values());
        for (var i = 0; i < childList.size(); i++) {
            var c = childList.get(i);
            if (!c.isVisible()) continue;
            var childLp = getLayoutParams(c);
            var columnSpec = childLp.columnSpec;
            var rowSpec = childLp.rowSpec;

            var colSpan = columnSpec.span;
            var rowSpan = rowSpec.span;

            var x1 = hLocations[colSpan.min];
            var y1 = vLocations[rowSpan.min];
            var x2 = hLocations[colSpan.max];
            var y2 = vLocations[rowSpan.max];

            var cellWidth = x2 - x1;
            var cellHeight = y2 - y1;

            var pWidth = getMeasurement(c, true);
            var pHeight = getMeasurement(c, false);

            var hAlign = columnSpec.getAbsoluteAlignment(true);
            var vAlign = rowSpec.getAbsoluteAlignment(false);

            var boundsX = horizontalAxis.getGroupBounds().getValue(i);
            var boundsY = verticalAxis.getGroupBounds().getValue(i);

            var gravityOffsetX = hAlign.getGravityOffset(c, cellWidth - boundsX.size(true));
            var gravityOffsetY = vAlign.getGravityOffset(c, cellHeight - boundsY.size(true));

            var leftMargin = getMargin(c, true, true);
            var topMargin = getMargin(c, false, true);

            var sumMarginsX = leftMargin + getMargin(c, true, false);
            var sumMarginsY = topMargin + getMargin(c, false, false);

            var alignmentOffsetX = boundsX.getOffset(c, hAlign, (int) (pWidth + sumMarginsX));
            var alignmentOffsetY = boundsY.getOffset(c, vAlign, (int) (pHeight + sumMarginsY));

            var width = hAlign.getSizeInCell(c, pWidth, cellWidth - sumMarginsX);
            var height = vAlign.getSizeInCell(c, pHeight, cellHeight - sumMarginsY);

            var dx = x1 + gravityOffsetX + alignmentOffsetX;
            var cx = paddingLeft + leftMargin + dx;
            var cy = paddingTop + y1 + gravityOffsetY + alignmentOffsetY + topMargin;

            if (width != c.getMeasuredWidth() || height != c.getMeasuredHeight()) {
                c.measure(new MeasureSpec(MeasureSpec.Mode.EXACTLY, width), new MeasureSpec(MeasureSpec.Mode.EXACTLY, height));
            }
            c.layout(cx, cy, cx + width, cy + height);
        }
    }

    private final class Axis {
        private static final int NEW = 0;
        private static final int PENDING = 1;
        private static final int COMPLETE = 2;

        public final boolean horizontal;
        public int definedCount = UNDEFINED;
        private int maxIndex = UNDEFINED;
        @Nullable
        private PackedMap<Spec, Bounds> groupBounds;
        public boolean groupBoundsValid = false;
        @Nullable
        private PackedMap<Interval, MutableInt> forwardLinks;
        public boolean forwardLinksValid = false;
        @Nullable
        private PackedMap<Interval, MutableInt> backwardLinks;
        public boolean backwardLinksValid = false;
        public int @Nullable [] leadingMargins;
        public boolean leadingMarginsValid = false;
        public int @Nullable [] trailingMargins;
        public boolean trailingMarginsValid = false;
        public Arc @Nullable [] arcs;
        public boolean arcsValid = false;
        public int @Nullable [] locations;
        public boolean locationsValid = false;
        public boolean hasWeights;
        public boolean hasWeightsValid = false;
        public int @Nullable [] deltas;
        private boolean orderPreserved = true;
        private final MutableInt parentMin = new MutableInt(0);
        private final MutableInt parentMax = new MutableInt(-MAX_SIZE);

        private final class Sorter {
            private final Arc[] result;
            private int cursor;
            private final Arc[][] arcsByVertex;
            private final int[] visited;

            Sorter(Arc[] arcs) {
                result = new Arc[arcs.length];
                cursor = result.length - 1;
                arcsByVertex = groupArcsByFirstVertex(arcs);
                visited = new int[getCount() + 1];
            }

            void walk(int loc) {
                switch (visited[loc]) {
                    case NEW -> {
                        visited[loc] = PENDING;
                        for (var arc : arcsByVertex[loc]) {
                            walk(arc.span.max);
                            result[cursor--] = arc;
                        }
                        visited[loc] = COMPLETE;
                    }
                    case PENDING -> throw new IllegalStateException("Circular dependency");
                    case COMPLETE -> {}
                }
            }

            Arc[] sort() {
                for (var loc = 0; loc < arcsByVertex.length; loc++) {
                    walk(loc);
                }
                if (cursor != -1) throw new IllegalStateException("Sorting failed");
                return result;
            }
        }

        private Axis(boolean horizontal) {
            this.horizontal = horizontal;
        }

        private int calculateMaxIndex() {
            var result = -1;
            for (var c : getChildren().values()) {
                var params = getLayoutParams(c);
                var spec = horizontal ? params.columnSpec : params.rowSpec;
                var span = spec.span;
                result = Math.max(result, span.min);
                result = Math.max(result, span.max);
                result = Math.max(result, span.size());
            }
            return result == -1 ? UNDEFINED : result;
        }

        private int getMaxIndex() {
            if (maxIndex == UNDEFINED) maxIndex = Math.max(0, calculateMaxIndex());
            return maxIndex;
        }

        public int getCount() {
            return Math.max(definedCount, getMaxIndex());
        }

        public void setCount(int count) {
            if (count != UNDEFINED && count < getMaxIndex()) {
                handleInvalidParams((horizontal ? "column" : "row") + "Count must be greater than or equal to the maximum of all grid indices (and spans) defined in the LayoutParams of each child");
            }
            definedCount = count;
        }

        public boolean isOrderPreserved() {
            return orderPreserved;
        }

        public void setOrderPreserved(boolean orderPreserved) {
            this.orderPreserved = orderPreserved;
            invalidateStructure();
        }

        private PackedMap<Spec, Bounds> createGroupBounds() {
            var assoc = Assoc.of(Spec.class, Bounds.class);
            for (var c : getChildren().values()) {
                var lp = getLayoutParams(c);
                var spec = horizontal ? lp.columnSpec : lp.rowSpec;
                var bounds = spec.getAbsoluteAlignment(horizontal).getBounds();
                assoc.put(spec, bounds);
            }
            return assoc.pack();
        }

        private void computeGroupBounds() {
            if (groupBounds == null) return;
            var values = groupBounds.values;
            for (var value : values) {
                value.reset();
            }
            var i = 0;
            for (var c : getChildren().values()) {
                var lp = getLayoutParams(c);
                var spec = horizontal ? lp.columnSpec : lp.rowSpec;
                var d = getDeltas();
                var size = getMeasurementIncludingMargin(c, horizontal) + (spec.weight == 0 ? 0 : d[i]);
                groupBounds.getValue(i).include(c, spec, this, (int) size);
                i++;
            }
        }

        public PackedMap<Spec, Bounds> getGroupBounds() {
            if (groupBounds == null) groupBounds = createGroupBounds();
            if (!groupBoundsValid) {
                computeGroupBounds();
                groupBoundsValid = true;
            }
            return groupBounds;
        }

        private PackedMap<Interval, MutableInt> createLinks(boolean min) {
            var result = Assoc.of(Interval.class, MutableInt.class);
            var keys = getGroupBounds().keys;
            for (var key : keys) {
                var span = min ? key.span : key.span.inverse();
                result.put(span, new MutableInt());
            }
            return result.pack();
        }

        private void computeLinks(PackedMap<Interval, MutableInt> links, boolean min) {
            var spans = links.values;
            for (var span : spans) {
                span.reset();
            }
            var bounds = getGroupBounds().values;
            for (var i = 0; i < bounds.length; i++) {
                var size = bounds[i].size(min);
                var valueHolder = links.getValue(i);
                valueHolder.value = Math.max(valueHolder.value, min ? size : -size);
            }
        }

        private PackedMap<Interval, MutableInt> getForwardLinks() {
            if (forwardLinks == null) forwardLinks = createLinks(true);
            if (!forwardLinksValid) {
                computeLinks(forwardLinks, true);
                forwardLinksValid = true;
            }
            return forwardLinks;
        }

        private PackedMap<Interval, MutableInt> getBackwardLinks() {
            if (backwardLinks == null) backwardLinks = createLinks(false);
            if (!backwardLinksValid) {
                computeLinks(backwardLinks, false);
                backwardLinksValid = true;
            }
            return backwardLinks;
        }

        private void include(List<Arc> arcs, Interval key, MutableInt size, boolean ignoreIfAlreadyPresent) {
            if (key.size() == 0) return;
            if (ignoreIfAlreadyPresent) {
                for (var arc : arcs) {
                    if (arc.span.equals(key)) return;
                }
            }
            arcs.add(new Arc(key, size));
        }

        private void include(List<Arc> arcs, Interval key, MutableInt size) {
            include(arcs, key, size, true);
        }

        private Arc[][] groupArcsByFirstVertex(Arc[] arcs) {
            var N = getCount() + 1;
            var result = new Arc[N][];
            var sizes = new int[N];
            for (var arc : arcs) {
                sizes[arc.span.min]++;
            }
            for (var i = 0; i < sizes.length; i++) {
                result[i] = new Arc[sizes[i]];
            }
            Arrays.fill(sizes, 0);
            for (var arc : arcs) {
                var i = arc.span.min;
                result[i][sizes[i]++] = arc;
            }
            return result;
        }

        private Arc[] topologicalSort(final Arc[] arcs) {
            return new Sorter(arcs).sort();
        }

        private Arc[] topologicalSort(List<Arc> arcs) {
            return topologicalSort(arcs.toArray(new Arc[0]));
        }

        private void addComponentSizes(List<Arc> result, PackedMap<Interval, MutableInt> links) {
            for (var i = 0; i < links.keys.length; i++) {
                var key = links.keys[i];
                include(result, key, links.values[i], false);
            }
        }

        private Arc[] createArcs() {
            var mins = new ArrayList<Arc>();
            var maxs = new ArrayList<Arc>();
            addComponentSizes(mins, getForwardLinks());
            addComponentSizes(maxs, getBackwardLinks());
            if (orderPreserved) {
                for (var i = 0; i < getCount(); i++) {
                    include(mins, new Interval(i, i + 1), new MutableInt(0));
                }
            }
            var N = getCount();
            include(mins, new Interval(0, N), parentMin, false);
            include(maxs, new Interval(N, 0), parentMax, false);
            var sMins = topologicalSort(mins);
            var sMaxs = topologicalSort(maxs);
            return append(sMins, sMaxs);
        }

        private void computeArcs() {
            getForwardLinks();
            getBackwardLinks();
        }

        public Arc[] getArcs() {
            if (arcs == null) arcs = createArcs();
            if (!arcsValid) {
                computeArcs();
                arcsValid = true;
            }
            return arcs;
        }

        private boolean relax(int[] locations, Arc entry) {
            if (!entry.valid) return false;
            var span = entry.span;
            var u = span.min;
            var v = span.max;
            var value = entry.value.value;
            var candidate = locations[u] + value;
            if (candidate > locations[v]) {
                locations[v] = candidate;
                return true;
            }
            return false;
        }

        private void init(int[] locations) {
            Arrays.fill(locations, 0);
        }

        private String arcsToString(List<Arc> arcs) {
            var varName = horizontal ? "x" : "y";
            var result = new StringBuilder();
            var first = true;
            for (var arc : arcs) {
                if (first) first = false;
                else result.append(", ");
                var src = arc.span.min;
                var dst = arc.span.max;
                var value = arc.value.value;
                result.append((src < dst) ? varName + dst + "-" + varName + src + ">=" + value : varName + src + "-" + varName + dst + "<=" + -value);
            }
            return result.toString();
        }

        private void logError(String axisName, Arc[] arcs, boolean[] culprits0) {
            var culprits = new ArrayList<Arc>();
            var removed = new ArrayList<Arc>();
            for (var c = 0; c < arcs.length; c++) {
                var arc = arcs[c];
                if (culprits0[c]) culprits.add(arc);
                if (!arc.valid) removed.add(arc);
            }
            AcademyCraft.LOGGER.error("{} constraints: {} are inconsistent; permanently removing: {}. ", axisName, arcsToString(culprits), arcsToString(removed));
        }

        private boolean solve(Arc[] arcs, int[] locations) {
            return solve(arcs, locations, true);
        }

        private boolean solve(Arc[] arcs, int[] locations, boolean modifyOnError) {
            var axisName = horizontal ? "horizontal" : "vertical";
            var N = getCount() + 1;
            boolean[] originalCulprits = null;

            for (var p = 0; p < arcs.length; p++) {
                init(locations);
                for (var i = 0; i < N; i++) {
                    var changed = false;
                    for (var arc : arcs) {
                        changed |= relax(locations, arc);
                    }
                    if (!changed) {
                        if (originalCulprits != null) logError(axisName, arcs, originalCulprits);
                        return true;
                    }
                }

                if (!modifyOnError) return false;

                var culprits = new boolean[arcs.length];
                for (var i = 0; i < N; i++) {
                    for (var j = 0; j < arcs.length; j++) {
                        culprits[j] |= relax(locations, arcs[j]);
                    }
                }

                if (p == 0) originalCulprits = culprits;

                for (var i = 0; i < arcs.length; i++) {
                    if (culprits[i]) {
                        var arc = arcs[i];
                        if (arc.span.min < arc.span.max) continue;
                        arc.valid = false;
                        break;
                    }
                }
            }
            return true;
        }

        private void computeMargins(boolean leading) {
            var margins = leading ? leadingMargins : trailingMargins;
            if (margins == null) return;
            for (var c : getChildren().values()) {
                if (!c.isVisible()) continue;
                var lp = getLayoutParams(c);
                var spec = horizontal ? lp.columnSpec : lp.rowSpec;
                var span = spec.span;
                var index = leading ? span.min : span.max;
                margins[index] = Math.max(margins[index], getMargin(c, horizontal, leading));
            }
        }

        public int @Nullable [] getLeadingMargins() {
            if (leadingMargins == null) leadingMargins = new int[getCount() + 1];
            if (!leadingMarginsValid) {
                computeMargins(true);
                leadingMarginsValid = true;
            }
            return leadingMargins;
        }

        public int @Nullable [] getTrailingMargins() {
            if (trailingMargins == null) trailingMargins = new int[getCount() + 1];
            if (!trailingMarginsValid) {
                computeMargins(false);
                trailingMarginsValid = true;
            }
            return trailingMargins;
        }

        private boolean solve(int[] a) {
            return solve(getArcs(), a);
        }

        private boolean computeHasWeights() {
            for (var child : getChildren().values()) {
                if (!child.isVisible()) continue;
                var lp = getLayoutParams(child);
                var spec = horizontal ? lp.columnSpec : lp.rowSpec;
                if (spec.weight != 0) return true;
            }
            return false;
        }

        private boolean hasWeights() {
            if (!hasWeightsValid) {
                hasWeights = computeHasWeights();
                hasWeightsValid = true;
            }
            return hasWeights;
        }

        public int [] getDeltas() {
            if (deltas == null) deltas = new int[getChildren().size()];
            return deltas;
        }

        private void shareOutDelta(int totalDelta, float totalWeight) {
            var d = getDeltas();
            Arrays.fill(d, 0);
            var i = 0;
            for (var c : getChildren().values()) {
                if (!c.isVisible()) {
                    i++;
                    continue;
                }
                var lp = getLayoutParams(c);
                var spec = horizontal ? lp.columnSpec : lp.rowSpec;
                var weight = spec.weight;
                if (weight != 0) {
                    var delta = Math.round((weight * totalDelta / totalWeight));
                    d[i] = delta;
                    totalDelta -= delta;
                    totalWeight -= weight;
                }
                i++;
            }
        }

        private void solveAndDistributeSpace(int[] a) {
            Arrays.fill(getDeltas(), 0);
            solve(a);
            var deltaMax = parentMin.value * getChildren().size() + 1;
            if (deltaMax < 2) return;

            var deltaMin = 0;
            var totalWeight = calculateTotalWeight();
            var validDelta = -1;
            var validSolution = true;

            while (deltaMin < deltaMax) {
                var delta = (int) (((long) deltaMin + deltaMax) / 2);
                invalidateValues();
                shareOutDelta(delta, totalWeight);
                validSolution = solve(getArcs(), a, false);
                if (validSolution) {
                    validDelta = delta;
                    deltaMin = delta + 1;
                } else {
                    deltaMax = delta;
                }
            }
            if (validDelta > 0 && !validSolution) {
                invalidateValues();
                shareOutDelta(validDelta, totalWeight);
                solve(a);
            }
        }

        private float calculateTotalWeight() {
            var totalWeight = 0f;
            for (var c : getChildren().values()) {
                if (!c.isVisible()) continue;
                var lp = getLayoutParams(c);
                var spec = horizontal ? lp.columnSpec : lp.rowSpec;
                totalWeight += spec.weight;
            }
            return totalWeight;
        }

        private void computeLocations(int[] a) {
            if (!hasWeights()) {
                solve(a);
            } else {
                solveAndDistributeSpace(a);
            }
            if (!orderPreserved) {
                var a0 = a[0];
                for (var i = 0; i < a.length; i++) {
                    a[i] = a[i] - a0;
                }
            }
        }

        public int @Nullable [] getLocations() {
            if (locations == null) locations = new int[getCount() + 1];
            if (!locationsValid) {
                computeLocations(locations);
                locationsValid = true;
            }
            return locations;
        }

        private int size(int[] locations) {
            return locations[getCount()];
        }

        private void setParentConstraints(int min, int max) {
            parentMin.value = min;
            parentMax.value = -max;
            locationsValid = false;
        }

        private float getMeasure(int min, int max) {
            setParentConstraints(min, max);
            var loc = getLocations();
            return loc == null ? 0 : size(loc);
        }

        public float getMeasure(MeasureSpec measureSpec) {
            var mode = measureSpec.getMode();
            var size = measureSpec.getSize();
            return switch (mode) {
                case UNSPECIFIED -> getMeasure(0, MAX_SIZE);
                case EXACTLY -> getMeasure((int) size, (int) size);
                case AT_MOST -> getMeasure(0, (int) size);
            };
        }

        public void layout(int size) {
            setParentConstraints(size, size);
            getLocations();
        }

        public void invalidateStructure() {
            maxIndex = UNDEFINED;
            groupBounds = null;
            forwardLinks = null;
            backwardLinks = null;
            leadingMargins = null;
            trailingMargins = null;
            arcs = null;
            locations = null;
            deltas = null;
            hasWeightsValid = false;
            invalidateValues();
        }

        public void invalidateValues() {
            groupBoundsValid = false;
            forwardLinksValid = false;
            backwardLinksValid = false;
            leadingMarginsValid = false;
            trailingMarginsValid = false;
            arcsValid = false;
            locationsValid = false;
        }
    }

    public static class LayoutParams extends WidgetContainer.LayoutParams {
        public Spec rowSpec = Spec.UNDEFINED;
        public Spec columnSpec = Spec.UNDEFINED;

        public LayoutParams() {
            this(Spec.UNDEFINED, Spec.UNDEFINED);
        }

        public LayoutParams(Spec rowSpec, Spec columnSpec) {
            this.rowSpec = rowSpec;
            this.columnSpec = columnSpec;
        }

        public LayoutParams(WidgetContainer.LayoutParams params) {
            super(params);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            rowSpec = source.rowSpec;
            columnSpec = source.columnSpec;
        }

        public void setGravity(int gravity) {
            rowSpec = rowSpec.copyWriteAlignment(getAlignment(gravity, false));
            columnSpec = columnSpec.copyWriteAlignment(getAlignment(gravity, true));
        }

        final void setRowSpecSpan(Interval span) {
            rowSpec = rowSpec.copyWriteSpan(span);
        }

        final void setColumnSpecSpan(Interval span) {
            columnSpec = columnSpec.copyWriteSpan(span);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            var that = (LayoutParams) o;
            return columnSpec.equals(that.columnSpec) && rowSpec.equals(that.rowSpec);
        }

        @Override
        public int hashCode() {
            var result = rowSpec.hashCode();
            result = 31 * result + columnSpec.hashCode();
            return result;
        }
    }

    private static final class Arc {
        public final Interval span;
        public final MutableInt value;
        public boolean valid = true;

        public Arc(Interval span, MutableInt value) {
            this.span = span;
            this.value = value;
        }

        @Override
        public String toString() {
            return span + " " + (!valid ? "+>" : "->") + " " + value;
        }
    }

    private static final class MutableInt {
        public int value;

        public MutableInt() {
            reset();
        }

        public MutableInt(int value) {
            this.value = value;
        }

        public void reset() {
            value = Integer.MIN_VALUE;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    private static final class Assoc<K, V> extends ArrayList<K> {
        private final Class<K> keyType;
        private final Class<V> valueType;
        private final ArrayList<V> values = new ArrayList<>();

        private Assoc(Class<K> keyType, Class<V> valueType) {
            this.keyType = keyType;
            this.valueType = valueType;
        }

        public static <K, V> Assoc<K, V> of(Class<K> keyType, Class<V> valueType) {
            return new Assoc<>(keyType, valueType);
        }

        public void put(K key, V value) {
            add(key);
            values.add(value);
        }

        @SuppressWarnings("unchecked")
        public PackedMap<K, V> pack() {
            var N = size();
            var keys = (K[]) Array.newInstance(keyType, N);
            var values = (V[]) Array.newInstance(valueType, N);
            for (var i = 0; i < N; i++) {
                keys[i] = get(i);
                values[i] = this.values.get(i);
            }
            return new PackedMap<>(keys, values);
        }
    }

    private static final class PackedMap<K, V> {
        public final int[] index;
        public final K[] keys;
        public final V[] values;

        private PackedMap(K[] keys, V[] values) {
            index = createIndex(keys);
            this.keys = compact(keys, index);
            this.values = compact(values, index);
        }

        public V getValue(int i) {
            return values[index[i]];
        }

        private static <K> int[] createIndex(K[] keys) {
            var size = keys.length;
            var result = new int[size];
            var keyToIndex = new HashMap<K, Integer>();
            for (var i = 0; i < size; i++) {
                var key = keys[i];
                var index = keyToIndex.computeIfAbsent(key, k -> keyToIndex.size());
                result[i] = index;
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        private static <K> K[] compact(K[] a, int[] index) {
            var size = a.length;
            var componentType = a.getClass().getComponentType();
            var result = (K[]) Array.newInstance(componentType, max2(index, -1) + 1);
            for (var i = 0; i < size; i++) {
                result[index[i]] = a[i];
            }
            return result;
        }
    }

    private static class Bounds {
        public int before;
        public int after;
        public int flexibility;

        private Bounds() {
            reset();
        }

        protected void reset() {
            before = Integer.MIN_VALUE;
            after = Integer.MIN_VALUE;
            flexibility = CAN_STRETCH;
        }

        protected void include(int before, int after) {
            this.before = Math.max(this.before, before);
            this.after = Math.max(this.after, after);
        }

        protected int size(boolean min) {
            if (!min && canStretch(flexibility)) return MAX_SIZE;
            return before + after;
        }

        protected int getOffset(Widget c, Alignment a, int size) {
            return before - a.getAlignmentValue(c, size);
        }

        protected final void include(Widget c, Spec spec, Axis axis, int size) {
            flexibility &= spec.getFlexibility();
            var alignment = spec.getAbsoluteAlignment(axis.horizontal);
            var before = alignment.getAlignmentValue(c, size);
            include(before, size - before);
        }

        @Override
        public String toString() {
            return "Bounds{" + "before=" + before + ", after=" + after + '}';
        }
    }

    public record Interval(int min, int max) {
        public int size() {
            return max - min;
        }

        public Interval inverse() {
            return new Interval(max, min);
        }

        @Override
        public String toString() {
            return "[" + min + ", " + max + "]";
        }
    }

    public static class Spec {
        static final Spec UNDEFINED = spec(GridLayoutWidget.UNDEFINED);
        static final float DEFAULT_WEIGHT = 0;
        final boolean startDefined;
        final Interval span;
        final Alignment alignment;
        final float weight;

        private Spec(boolean startDefined, Interval span, Alignment alignment, float weight) {
            this.startDefined = startDefined;
            this.span = span;
            this.alignment = alignment;
            this.weight = weight;
        }

        private Spec(boolean startDefined, int start, int size, Alignment alignment, float weight) {
            this(startDefined, new Interval(start, start + size), alignment, weight);
        }

        private Alignment getAbsoluteAlignment(boolean horizontal) {
            if (alignment != UNDEFINED_ALIGNMENT) return alignment;
            if (weight == 0f) return horizontal ? START : TOP;
            return FILL;
        }

        final Spec copyWriteSpan(Interval span) {
            return new Spec(startDefined, span, alignment, weight);
        }

        final Spec copyWriteAlignment(Alignment alignment) {
            return new Spec(startDefined, span, alignment, weight);
        }

        final int getFlexibility() {
            return (alignment == UNDEFINED_ALIGNMENT && weight == 0) ? INFLEXIBLE : CAN_STRETCH;
        }

        @Override
        public boolean equals(@Nullable Object that) {
            if (this == that) return true;
            if (that == null || getClass() != that.getClass()) return false;
            var spec = (Spec) that;
            return startDefined == spec.startDefined &&
                    Float.compare(spec.weight, weight) == 0 &&
                    alignment.equals(spec.alignment) &&
                    span.equals(spec.span);
        }

        @Override
        public int hashCode() {
            return Objects.hash(startDefined, span, alignment, weight);
        }
    }

    public static abstract class Alignment {
        abstract int getGravityOffset(Widget view, int cellDelta);

        abstract int getAlignmentValue(Widget view, int viewSize);

        int getSizeInCell(Widget view, float viewSize, int cellSize) {
            return (int) viewSize;
        }

        Bounds getBounds() {
            return new Bounds();
        }
    }

    private static final Alignment UNDEFINED_ALIGNMENT = new Alignment() {
        @Override
        int getGravityOffset(Widget view, int cellDelta) {
            return UNDEFINED;
        }

        @Override
        public int getAlignmentValue(Widget view, int viewSize) {
            return UNDEFINED;
        }
    };

    private static final Alignment LEADING = new Alignment() {
        @Override
        int getGravityOffset(Widget view, int cellDelta) {
            return 0;
        }

        @Override
        public int getAlignmentValue(Widget view, int viewSize) {
            return 0;
        }
    };

    private static final Alignment TRAILING = new Alignment() {
        @Override
        int getGravityOffset(Widget view, int cellDelta) {
            return cellDelta;
        }

        @Override
        public int getAlignmentValue(Widget view, int viewSize) {
            return viewSize;
        }
    };

    public static final Alignment TOP = LEADING;
    public static final Alignment BOTTOM = TRAILING;
    public static final Alignment START = LEADING;
    public static final Alignment END = TRAILING;
    public static final Alignment LEFT = START;
    public static final Alignment RIGHT = END;

    public static final Alignment CENTER = new Alignment() {
        @Override
        int getGravityOffset(Widget view, int cellDelta) {
            return cellDelta >> 1;
        }

        @Override
        public int getAlignmentValue(Widget view, int viewSize) {
            return viewSize >> 1;
        }
    };

    public static final Alignment FILL = new Alignment() {
        @Override
        int getGravityOffset(Widget view, int cellDelta) {
            return 0;
        }

        @Override
        public int getAlignmentValue(Widget view, int viewSize) {
            return UNDEFINED;
        }

        @Override
        public int getSizeInCell(Widget view, float viewSize, int cellSize) {
            return cellSize;
        }
    };

    private static boolean canStretch(int flexibility) {
        return (flexibility & CAN_STRETCH) != 0;
    }

    private static final int INFLEXIBLE = 0;
    private static final int CAN_STRETCH = 2;
}