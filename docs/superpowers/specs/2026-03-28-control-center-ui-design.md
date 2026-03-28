# Pico Signal Processor: "Control Center" UI Overhaul

## 1. Goal Description
The "Control Center" UI design aims to transform the Pico Signal Processor from a multi-box configuration into a streamlined, action-oriented dashboard. The primary goal is to minimize clutter by focusing on the core user flow (Folder Selection -> Start Processing -> Result Viewing).

## 2. Component Design

### 🎯 Section 1: The "Drop Zone" & Folder Selection
The traditional browse-button section is replaced with a single, large interactive card.
*   **Visuals**: Spacious card with dashed borders and a large "Folder" icon.
*   **Drag & Drop**: Native OS support for dropping a folder directly onto the zone to select it as the **Input Directory**.
*   **Aesthetic Feedback**: Subtle color shifts on hover with a file and a "breadcrumb" path display for the currently selected folder.

### ⚡ Section 2: The "Action Center" & Progress
The processing and status area are integrated below the selection card.
*   **The Main Action Button**: A wide, high-contrast button that serves as the single source of truth for the processing state.
    *   **Idle**: "Start Processing" (Material 3 Purple theme).
    *   **Processing**: Changes to "Processing..." with a spinning activity icon.
    *   **Success**: Transforms into a vibrant green "**Finished**" button with a checkmark.
*   **Progress**: A thicker, glowing progress bar integrated into the bottom of the main Action Center block, emphasizing the current task's movement.
*   **The "Quick-Link" Output**: A clean status line displays the auto-calculated output path (`[Input_Folder]_PSP_Output`) with a subtle "[Change]" link for manual browsing.
*   **Success Actions**: When finished, an "Open Output Folder" button with a folder-open icon appears prominently next to the finished state.

### 📁 Section 3: The "Log Tray" & Summary
Logs are treated as secondary technical details rather than a permanent focal point.
*   **Log Tray**: A collapsed-by-default header labeled "**View Processing Details**" (with an expand/collapse arrow).
*   **Expansion Logic**:
    *   **Manual**: Expandable via click.
    *   **Reactive**: Automatically expands if an error is detected.
*   **Running Summary**: During processing, a small text line appears next to the progress bar (e.g., `Processing: file_1.csv`) to show activity without overwhelming the user.

## 3. Architecture & Data Flow
*   **MainViewModel**: No major architectural shifts, but new `isLogExpanded` and `dragHoverState` flags will be introduced to drive the UI.
*   **HomeScreen**: Re-structured entirely into single-card-based Layout.
*   **SettingsScreen**: Remains consistent but can incorporate the relocated "Custom Output Path" if needed for a "Power User" experience.

## 4. Verification Plan

### Manual Verification
- Verify that dragging a folder onto the Drop Zone correctly sets the input path.
- Confirm the "Quick-Link" for output allows selection without cluttering the screen.
- Ensure the Log Tray only expands as intended and provides essential feedback on failure.
- Validate that the progress bar doesn't "fight" with the action button's space during processing.
