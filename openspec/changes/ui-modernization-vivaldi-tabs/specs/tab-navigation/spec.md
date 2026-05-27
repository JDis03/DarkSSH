## ADDED Requirements

### Requirement: User can switch tabs via tap

The system SHALL allow users to switch between tabs by tapping on the tab in the tab bar. The selected tab's content SHALL be displayed in the main content area.

#### Scenario: Tap tab to switch
- **WHEN** user taps on an inactive tab in the tab bar
- **THEN** system animates the transition and displays the tapped tab's content (SSH terminal or SFTP browser)

#### Scenario: Tap already active tab
- **WHEN** user taps on the currently active tab
- **THEN** system takes no action (tab remains selected)

#### Scenario: Switch preserves scroll position
- **WHEN** user switches from tab A to tab B and then back to tab A
- **THEN** system restores the scroll position and terminal output from tab A exactly as it was

### Requirement: User can swipe between tabs

The system SHALL support horizontal swipe gestures to navigate between adjacent tabs. Swipe gestures SHALL provide smooth animations and haptic feedback.

#### Scenario: Swipe right to previous tab
- **WHEN** user swipes right on the main content area
- **THEN** system navigates to the previous tab (left adjacent) with slide animation

#### Scenario: Swipe left to next tab
- **WHEN** user swipes left on the main content area
- **THEN** system navigates to the next tab (right adjacent) with slide animation

#### Scenario: Swipe at first tab
- **WHEN** user swipes right while viewing the first tab
- **THEN** system displays edge glow effect and does not navigate

#### Scenario: Swipe at last tab
- **WHEN** user swipes left while viewing the last tab
- **THEN** system displays edge glow effect and does not navigate

#### Scenario: Swipe provides haptic feedback
- **WHEN** user completes a swipe gesture to change tabs
- **THEN** system provides subtle haptic feedback (TICK vibration)

### Requirement: Tab bar auto-scrolls to selected tab

The system SHALL automatically scroll the tab bar horizontally to keep the selected tab visible when it would otherwise be off-screen.

#### Scenario: Select off-screen tab via API
- **WHEN** system selects a tab that is currently scrolled out of view
- **THEN** system animates the tab bar scroll to center the selected tab

#### Scenario: Create new tab beyond viewport
- **WHEN** user creates a new tab that would appear beyond the right edge of the screen
- **THEN** system scrolls the tab bar to show the newly created tab

#### Scenario: Navigate with keyboard next
- **WHEN** user presses Ctrl+Tab (or equivalent) to move to next tab
- **THEN** system scrolls tab bar to keep the newly selected tab centered

### Requirement: Keyboard shortcuts navigate tabs

The system SHALL support keyboard shortcuts for tab navigation when a physical keyboard is connected. This enables power users to work more efficiently.

#### Scenario: Ctrl+Tab to next tab
- **WHEN** user presses Ctrl+Tab on physical keyboard
- **THEN** system selects the next tab to the right (wraps to first tab if at end)

#### Scenario: Ctrl+Shift+Tab to previous tab
- **WHEN** user presses Ctrl+Shift+Tab on physical keyboard
- **THEN** system selects the previous tab to the left (wraps to last tab if at start)

#### Scenario: Ctrl+W to close tab
- **WHEN** user presses Ctrl+W on physical keyboard
- **THEN** system closes the currently selected tab (with confirmation if needed)

#### Scenario: Ctrl+T to new tab
- **WHEN** user presses Ctrl+T on physical keyboard
- **THEN** system displays the new tab dialog

#### Scenario: Ctrl+1 through Ctrl+9 to select specific tab
- **WHEN** user presses Ctrl+[1-9] on physical keyboard
- **THEN** system selects the tab at that position (1 = first tab, 2 = second tab, etc.)

#### Scenario: No keyboard connected
- **WHEN** user has no physical keyboard connected
- **THEN** system does not display keyboard shortcut hints but shortcuts remain functional if software keyboard supports them

### Requirement: Tab bar indicates scroll state

The system SHALL display visual indicators when more tabs exist beyond the visible area, helping users discover hidden tabs.

#### Scenario: Show left scroll indicator
- **WHEN** tab bar is scrolled and tabs exist to the left of the viewport
- **THEN** system displays a gradient fade or shadow on the left edge of the tab bar

#### Scenario: Show right scroll indicator
- **WHEN** tabs exist to the right of the viewport
- **THEN** system displays a gradient fade or shadow on the right edge of the tab bar

#### Scenario: No scroll indicators when all tabs visible
- **WHEN** all tabs fit within the tab bar width
- **THEN** system does not display any scroll indicators

### Requirement: Tab selection state is observable

The system SHALL expose the selected tab ID as an observable StateFlow so UI components can react to tab changes.

#### Scenario: Observe selected tab changes
- **WHEN** TabManager emits a new selectedTabId value
- **THEN** UI components collecting the StateFlow receive the update and recompose accordingly

#### Scenario: Multiple collectors receive updates
- **WHEN** both the tab bar and content pager observe selectedTabId
- **THEN** both components update simultaneously when tab selection changes

### Requirement: Tab navigation respects Material Design motion

The system SHALL use Material Design motion principles for all tab navigation animations including duration, easing curves, and shared element transitions.

#### Scenario: Tab switch animation duration
- **WHEN** user switches tabs via tap or swipe
- **THEN** system completes the transition animation in 300ms using FastOutSlowIn easing

#### Scenario: Tab create animation
- **WHEN** system creates a new tab
- **THEN** tab animates into view from the right edge using spring animation

#### Scenario: Tab close animation
- **WHEN** system closes a tab
- **THEN** tab fades out and remaining tabs slide together using emphasized decelerate easing

#### Scenario: Reduced motion preference
- **WHEN** user has enabled "Reduce animations" in system accessibility settings
- **THEN** system uses instant transitions without animations while preserving functionality
