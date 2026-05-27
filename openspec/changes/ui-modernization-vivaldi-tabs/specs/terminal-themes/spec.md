## ADDED Requirements

### Requirement: Terminal supports color scheme presets

The system SHALL provide predefined terminal color schemes including Dracula, Nord, One Dark, and Solarized Dark. Each scheme SHALL define 16 ANSI colors plus foreground, background, and cursor colors.

#### Scenario: Apply Dracula theme
- **WHEN** user selects Dracula theme in settings
- **THEN** system applies background #282A36, foreground #F8F8F2, and Dracula's 16 ANSI colors to the terminal renderer

#### Scenario: Apply Nord theme
- **WHEN** user selects Nord theme in settings
- **THEN** system applies Nord color palette (background #2E3440, foreground #D8DEE9, etc.)

#### Scenario: Apply One Dark theme
- **WHEN** user selects One Dark theme in settings
- **THEN** system applies One Dark color palette (background #282C34, foreground #ABB2BF, etc.)

#### Scenario: Apply Solarized Dark theme
- **WHEN** user selects Solarized Dark theme in settings
- **THEN** system applies Solarized Dark palette (background #002B36, foreground #839496, etc.)

### Requirement: Terminal theme persists per tab

The system SHALL allow each SSH terminal tab to have its own theme selection, enabling users to visually differentiate connections.

#### Scenario: Set theme for specific tab
- **WHEN** user long-presses an SSH tab and selects "Theme" then chooses Nord
- **THEN** system applies Nord theme only to that tab and saves the association

#### Scenario: Create new tab with default theme
- **WHEN** user creates a new SSH terminal tab
- **THEN** system applies the global default theme (configurable in settings)

#### Scenario: Restore tab-specific theme
- **WHEN** app restores tabs from database
- **THEN** system applies the correct theme to each tab based on saved preference

### Requirement: Terminal theme includes font customization

The system SHALL allow users to select terminal font family (JetBrains Mono, Fira Code, Ubuntu Mono, Roboto Mono) and adjust font size independently of the color scheme.

#### Scenario: Select JetBrains Mono font
- **WHEN** user selects JetBrains Mono in terminal settings
- **THEN** system loads JetBrains Mono font file and applies it to all terminal tabs

#### Scenario: Select Fira Code with ligatures
- **WHEN** user selects Fira Code in terminal settings
- **THEN** system enables ligature rendering (drawTextRun with ligatures) for operators like -> == !=

#### Scenario: Adjust font size
- **WHEN** user changes terminal font size slider to 14sp
- **THEN** system reflows terminal text with new font size and updates character grid dimensions

#### Scenario: Font size respects accessibility
- **WHEN** user has system font scaling enabled (Large Text accessibility)
- **THEN** system scales terminal font proportionally while maintaining monospace grid alignment

### Requirement: Terminal theme data is serializable

The system SHALL store terminal themes as Kotlin objects with serializable data classes, enabling future import/export of custom themes.

#### Scenario: Serialize theme to JSON
- **WHEN** system needs to export Dracula theme
- **THEN** system serializes TerminalTheme data class to JSON with all color values in hex format

#### Scenario: Deserialize theme from JSON
- **WHEN** system imports a custom theme JSON file
- **THEN** system parses JSON into TerminalTheme object and validates all color fields are valid hex codes

### Requirement: Terminal theme preview is available

The system SHALL display a live preview of the selected theme showing sample terminal output with syntax highlighting and color examples.

#### Scenario: Show theme preview in settings
- **WHEN** user browses terminal themes in settings
- **THEN** system displays a preview box showing "user@host:~$" prompt and sample commands in the selected theme colors

#### Scenario: Update preview on theme selection
- **WHEN** user taps a different theme in the list
- **THEN** system immediately updates the preview to show the new theme without applying it yet

#### Scenario: Preview shows all 16 colors
- **WHEN** preview is displayed
- **THEN** system shows a color palette grid with all 16 ANSI colors (8 normal + 8 bright)
