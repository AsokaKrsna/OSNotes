# TextBox Zoom Optimization - Complexity Analysis

## Current Problem

The textbox tool doesn't scale properly with zoom. When you zoom in/out, the textbox behaves incorrectly because it uses **screen space coordinates** while all other tools (pen, highlighter, shapes, eraser) use **bitmap space coordinates**.

## Why It's Complex

### 1. **Dual Coordinate System Issue** 🔴 HIGH COMPLEXITY

**Current Implementation:**
- **Strokes/Shapes/Eraser**: Store coordinates in **bitmap space** (e.g., 0-595 for A4 width)
  - Drawing: Transform bitmap → screen with zoom/pan
  - Touch: Transform screen → bitmap, store in bitmap space
  - Result: Works perfectly at any zoom level

- **TextBox**: Stores coordinates in **SCREEN SPACE** (e.g., 0-1080 for phone width)
  - Drawing: Uses screen coordinates directly
  - Touch: Uses screen coordinates directly
  - Result: Breaks when zoom changes

**Example:**
```kotlin
// Strokes (CORRECT - bitmap space)
fun handleTouchDown(event: MotionEvent) {
    val screenPos = Offset(event.x, event.y)
    val bitmapPos = transformToBitmapSpace(screenPos)  // ✅ Transform to bitmap
    currentPoints = listOf(StrokePoint(bitmapPos.x, bitmapPos.y))
}

// TextBox (INCORRECT - screen space)
fun startTextBoxDrawing(startPoint: Offset) {
    textBoxState = TextBoxState(
        bounds = Rect(startPoint, startPoint)  // ❌ Stored in screen space!
    )
}
```

### 2. **Multiple Touch Handlers Need Updates** 🟡 MEDIUM COMPLEXITY

The textbox has **5 different interaction modes**, each with its own coordinate handling:

```kotlin
enum class TextBoxMode {
    NONE,           // No textbox
    DRAWING,        // Drawing initial bounds (drag to create)
    EDITING,        // Typing text inside
    POSITIONING     // Dragging to move OR resizing with handles
}
```

**Each mode requires coordinate transformation:**

1. **DRAWING mode** (InkingCanvas.kt, lines 570-600)
   - `startTextBoxDrawing()` - needs bitmap space
   - `updateTextBoxBounds()` - needs bitmap space
   - `finishTextBoxDrawing()` - needs bitmap space

2. **POSITIONING mode** (InkingCanvas.kt, lines 643-789)
   - `startTextBoxDrag()` - hit detection needs screen space comparison
   - `updateTextBoxDrag()` - delta calculation needs bitmap space
   - `finishTextBoxDrag()` - final bounds need bitmap space
   - `resizeTextBox()` - 4 resize handles, each needs bitmap space deltas

3. **Rendering** (InkingCanvas.kt, lines 1041-1081)
   - `drawTextBox()` - transforms bitmap → screen for display
   - Currently does this transformation, but source data is wrong

### 3. **Resize Handle Hit Detection** 🔴 HIGH COMPLEXITY

The resize handles are the trickiest part:

```kotlin
// Current implementation (InkingCanvas.kt, lines 400-450)
val displayBounds = Rect(
    bounds.left * displayScale + offsetX,    // Transform to screen
    bounds.top * displayScale + offsetY,
    bounds.right * displayScale + offsetX,
    bounds.bottom * displayScale + offsetY
)

val handles = mapOf(
    "top-left" to Offset(displayBounds.left, displayBounds.top),
    "top-right" to Offset(displayBounds.right, displayBounds.top),
    // ... etc
)

// Check if touch hits a handle (in SCREEN space)
handles.forEach { (handle, handlePos) ->
    val distance = sqrt(
        (screenPos.x - handlePos.x)² + (screenPos.y - handlePos.y)²
    )
    if (distance <= handleTouchRadius) {
        // Start resizing
    }
}
```

**Problem:** This works NOW because bounds are in screen space. If we change bounds to bitmap space:
- Need to transform bounds → screen for hit detection
- Need to transform touch → bitmap for resize deltas
- Need to ensure handle positions update correctly with zoom
- Need to maintain 48dp minimum touch target (accessibility)

### 4. **State Management Complexity** 🟡 MEDIUM COMPLEXITY

The `TextBoxState` stores multiple coordinate-related fields:

```kotlin
data class TextBoxState(
    val bounds: Rect = Rect.Zero,           // ❌ Currently screen space
    val dragOffset: Offset = Offset.Zero    // ❌ Currently screen space
)
```

**Changes needed:**
- `bounds` must be in bitmap space
- `dragOffset` must be in bitmap space
- `getTransformedBounds()` must apply zoom/pan transform
- All ViewModel functions must work with bitmap space

### 5. **Text Rendering Complications** 🟡 MEDIUM COMPLEXITY

Text rendering needs to scale with zoom:

```kotlin
// Current (InkingCanvas.kt, drawTextBox)
val paint = android.graphics.Paint().apply {
    textSize = 16f * scale  // ✅ Already scales
}

// But the bounds are wrong, so text appears in wrong location
```

**Additional issues:**
- Font size needs to scale with zoom
- Line wrapping needs to recalculate with zoomed width
- Padding needs to scale
- Text cursor position needs to scale

### 6. **Drag Offset Accumulation** 🟡 MEDIUM COMPLEXITY

The drag system uses incremental offsets:

```kotlin
fun updateTextBoxDrag(delta: Offset) {
    textBoxState = currentState.copy(
        dragOffset = currentState.dragOffset + delta  // Accumulates
    )
}

fun finishTextBoxDrag() {
    val newBounds = Rect(
        bounds.left + dragOffset.x,   // Apply accumulated offset
        bounds.top + dragOffset.y,
        bounds.right + dragOffset.x,
        bounds.bottom + dragOffset.y
    )
}
```

**Problem:** If zoom changes DURING drag:
- Accumulated offset is in old zoom scale
- New deltas are in new zoom scale
- Results in jump/glitch

## What Needs to Change

### Files to Modify:

1. **EditorModels.kt** (Low effort)
   - Update `TextBoxState` documentation to clarify bitmap space
   - No code changes needed (just data container)

2. **EditorViewModel.kt** (Medium effort - 8 functions)
   - `startTextBoxDrawing()` - accept bitmap space
   - `updateTextBoxBounds()` - work in bitmap space
   - `updateTextBoxDrag()` - deltas in bitmap space
   - `resizeTextBox()` - deltas in bitmap space
   - `finishTextBoxDrag()` - bounds in bitmap space
   - `finalizeTextBox()` - already correct (saves to TextAnnotation)
   - `startTextBoxDrag()` - hit detection needs transform
   - Add helper: `transformScreenToBitmap()` function

3. **InkingCanvas.kt** (High effort - 15+ locations)
   - `handleTouchDown()` for TEXT tool - transform to bitmap
   - `handleTouchMove()` for TEXT tool - transform to bitmap
   - `handleTouchUp()` for TEXT tool - transform to bitmap
   - Resize handle hit detection - transform bounds to screen
   - Resize handle positions - transform to screen
   - `drawTextBox()` - already transforms, but verify
   - All 4 resize handle calculations - use bitmap deltas

4. **EditorScreen.kt** (Low effort)
   - Verify callbacks pass correct coordinate space
   - No changes likely needed

## Estimated Effort

### Time Breakdown:
- **Understanding current flow**: 30 minutes ✅ (done)
- **Refactor ViewModel functions**: 1 hour
- **Refactor InkingCanvas touch handlers**: 1.5 hours
- **Fix resize handle hit detection**: 1 hour
- **Testing at different zoom levels**: 1 hour
- **Bug fixes and edge cases**: 1 hour
- **Total: ~5.5 hours**

### Risk Level: **MEDIUM-HIGH**

**Why risky:**
- Easy to introduce subtle bugs (off-by-one pixel errors)
- Hard to test all combinations (zoom + drag + resize)
- Potential for coordinate space confusion
- May break existing textbox functionality temporarily

## Recommended Approach

### Option 1: Full Refactor (5.5 hours)
**Pros:**
- Textbox works perfectly at all zoom levels
- Consistent with other tools
- Future-proof

**Cons:**
- Time-consuming
- Risk of introducing bugs
- Requires extensive testing

### Option 2: Quick Fix (30 minutes)
**Disable textbox when zoomed:**

```kotlin
// In EditorScreen.kt
if (toolState.currentTool == AnnotationTool.TEXT && zoomScale != 1f) {
    LaunchedEffect(Unit) {
        Toast.makeText(context, "Zoom out to use text tool", Toast.LENGTH_SHORT).show()
        onToolSelected(AnnotationTool.PEN)
    }
}
```

**Pros:**
- Prevents user frustration
- No risk of bugs
- Quick to implement

**Cons:**
- Not a real solution
- Limits functionality

### Option 3: Hybrid Approach (2 hours)
**Implement basic zoom support without resize:**

1. Store bounds in bitmap space
2. Transform for drawing/hit detection
3. Disable resize handles when zoomed
4. Allow drag-to-move only

**Pros:**
- Most common use case works (create + move)
- Less complex than full solution
- Lower risk

**Cons:**
- No resize when zoomed
- Still requires significant work

## Recommendation

**For production release:** Use **Option 2** (Quick Fix)
- Users can zoom out to add text, then zoom in to view
- Zero risk of breaking existing functionality
- Can implement full solution in next version based on user feedback

**For complete solution:** Use **Option 1** (Full Refactor)
- Only if textbox is a critical feature
- Allocate full day for implementation + testing
- Consider it a v2.0 feature

## Code Example: Full Refactor

Here's what the main change would look like:

```kotlin
// BEFORE (screen space)
fun startTextBoxDrawing(startPoint: Offset) {
    textBoxState = TextBoxState(
        bounds = Rect(startPoint, startPoint)  // Screen space ❌
    )
}

// AFTER (bitmap space)
fun startTextBoxDrawing(startPoint: Offset) {
    // startPoint is already in bitmap space from InkingCanvas transform
    textBoxState = TextBoxState(
        bounds = Rect(startPoint, startPoint)  // Bitmap space ✅
    )
}

// In InkingCanvas.kt - handleTouchDown for TEXT
AnnotationTool.TEXT -> {
    val screenPos = Offset(event.x, event.y)
    val bitmapPos = transformToBitmapSpace(screenPos.x, screenPos.y)  // ✅ Add this
    onTextBoxStart(bitmapPos)  // Pass bitmap coordinates
}

// In InkingCanvas.kt - drawTextBox
private fun DrawScope.drawTextBox(textBoxState: TextBoxState, scale: Float, offsetX: Float, offsetY: Float) {
    val bounds = textBoxState.bounds  // Now in bitmap space
    
    // Transform to screen space for drawing
    val screenLeft = bounds.left * scale + offsetX
    val screenTop = bounds.top * scale + offsetY
    val screenWidth = bounds.width * scale
    val screenHeight = bounds.height * scale
    
    drawRect(
        color = Color.Blue,
        topLeft = Offset(screenLeft, screenTop),
        size = Size(screenWidth, screenHeight)
    )
}
```

## Conclusion

The textbox zoom optimization is **moderately complex** (5.5 hours) because:

1. ❌ Coordinate system mismatch (screen vs bitmap)
2. ❌ Multiple interaction modes to update
3. ❌ Complex resize handle hit detection
4. ❌ State management across ViewModel + Canvas
5. ❌ Risk of subtle coordinate bugs

**It's not impossible, but it's not trivial either.** The complexity comes from the architectural decision to use screen space for textbox while everything else uses bitmap space. Fixing it requires touching ~20 locations across 3 files and careful testing.

For a production app, I'd recommend the **Quick Fix** (disable when zoomed) and implement the full solution only if users specifically request it.
