## ADDED Requirements

### Requirement: App uses dark theme with Termius-inspired palette

The system SHALL apply a modern dark theme with deep backgrounds (#1A1A1A, #2B2B2B), cyan accent color (#00B8D4), and elevated surfaces following Material 3 elevation tokens.

#### Scenario: Apply dark background colors
- **WHEN** app displays any screen
- **THEN** system uses background color #1A1A1A for main surface and #2B2B2B for elevated cards

#### Scenario: Apply accent color to interactive elements
- **WHEN** system displays buttons, tabs, or selection indicators
- **THEN** system uses cyan #00B8D4 as the primary accent color

#### Scenario: Apply elevation to cards
- **WHEN** system displays cards in SFTP browser or settings
- **THEN** system applies 2dp elevation with appropriate shadow and surface tint

### Requirement: Theme provides Material 3 color tokens

The system SHALL extend MaterialTheme with semantic color tokens that reference the dark theme palette. All UI components SHALL use semantic tokens instead of hardcoded colors.

#### Scenario: Use semantic surface colors
- **WHEN** composable accesses MaterialTheme.colorScheme.surface
- **THEN** system returns #1A1A1A (dark background)

#### Scenario: Use semantic primary color
- **WHEN** composable accesses MaterialTheme.colorScheme.primary
- **THEN** system returns #00B8D4 (cyan accent)

#### Scenario: Use semantic onSurface color
- **WHEN** composable needs text color on surface
- **THEN** system returns #FFFFFF with 87% opacity (high emphasis white)

### Requirement: Theme supports elevation tonal system

The system SHALL apply Material 3 elevation tonal system where elevated surfaces have subtle color tints based on primary color, creating depth perception without heavy shadows.

#### Scenario: Elevated card receives tonal tint
- **WHEN** card has 2dp elevation
- **THEN** system applies 5% cyan tint overlay on the surface color

#### Scenario: Higher elevation increases tint
- **WHEN** bottom sheet has 16dp elevation
- **THEN** system applies 12% cyan tint overlay creating stronger depth perception

### Requirement: Typography uses Material 3 type scale

The system SHALL define typography tokens for display, headline, title, body, and label text following Material 3 type scale with Roboto font family.

#### Scenario: Display large text
- **WHEN** UI shows display text (hero headers)
- **THEN** system uses Roboto 57sp / 64sp line height / -0.25sp letter spacing

#### Scenario: Body text
- **WHEN** UI shows body text (paragraphs, descriptions)
- **THEN** system uses Roboto 14sp / 20sp line height / 0.25sp letter spacing

#### Scenario: Label text
- **WHEN** UI shows label text (buttons, tabs)
- **THEN** system uses Roboto Medium 14sp / 20sp line height / 0.1sp letter spacing

### Requirement: Theme persists user preference

The system SHALL save theme preference to DataStore and restore it on app launch. Future enhancement: support light theme toggle.

#### Scenario: Save theme preference
- **WHEN** app applies dark theme on first launch
- **THEN** system saves "dark" to DataStore preferences

#### Scenario: Restore theme on launch
- **WHEN** app launches and DataStore contains theme preference
- **THEN** system applies the saved theme before rendering UI

#### Scenario: Default to dark theme
- **WHEN** app launches without theme preference in DataStore
- **THEN** system defaults to dark theme and saves this preference
