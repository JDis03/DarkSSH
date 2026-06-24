# Converting SVG Icons to Android Vector Drawable

## Current Status

The SFTP file browser uses Material Icons as placeholders. Custom SVG icons from `candy-icons` and `sweet-folders` repositories are available but need conversion to Android Vector Drawable XML format.

## Why Conversion is Needed

Android **cannot use SVG files directly** in `res/drawable/`. They must be converted to Vector Drawable XML format.

## Available SVG Icons

Located in project root:
- `candy-icons/mimetypes/scalable/` - File type icons
- `sweet-folders/Sweet-Rainbow/Places/48/` - Folder icons

## How to Convert (Using Android Studio)

### Method 1: Android Studio Vector Asset Tool (Recommended)

1. Open project in Android Studio
2. Right-click on `app/src/main/res/drawable/`
3. Select **New → Vector Asset**
4. Choose **Local file (SVG, PSD)**
5. Click folder icon and select SVG file
6. Set name (e.g., `ic_file_python` for Python files)
7. Adjust size if needed (default 24dp is good)
8. Click **Next** → **Finish**
9. Android Studio creates the XML automatically

### Method 2: Online Converter

If Android Studio conversion fails (complex gradients):
1. Go to https://svg2vector.com/
2. Upload SVG file
3. Download generated XML
4. Place in `app/src/main/res/drawable/`

### Method 3: Command Line (svg2vd)

```bash
# Install svg2vd
npm install -g svg2vd

# Convert single file
svg2vd -i input.svg -o ic_file_python.xml

# Batch convert
for file in candy-icons/mimetypes/scalable/*.svg; do
    name=$(basename "$file" .svg)
    svg2vd -i "$file" -o "app/src/main/res/drawable/ic_file_${name}.xml"
done
```

## Icons to Convert

### File Type Icons (from candy-icons)

| File Type | SVG Source | Target Name |
|-----------|------------|-------------|
| Python | `application-x-python-bytecode.svg` | `ic_file_python.xml` |
| Shell | `application-x-shellscript.svg` | `ic_file_shell.xml` |
| JavaScript | `application-javascript.svg` | `ic_file_javascript.xml` |
| Java | `application-x-java.svg` | `ic_file_java.xml` |
| PHP | `application-x-php.svg` | `ic_file_php.xml` |
| Ruby | `application-x-ruby.svg` | `ic_file_ruby.xml` |
| Go | `text-x-go.svg` | `ic_file_go.xml` |
| Rust | `text-rust.svg` | `ic_file_rust.xml` |
| C/C++ | `text-x-c++src.svg` | `ic_file_cpp.xml` |
| Archive | `application-x-archive.svg` | `ic_file_archive.xml` |
| PDF | `application-pdf.svg` | `ic_file_pdf.xml` |
| Image | `image-x-generic.svg` | `ic_file_image.xml` |
| Video | `video-x-generic.svg` | `ic_file_video.xml` |
| Audio | `audio-x-generic.svg` | `ic_file_audio.xml` |
| Text | `text-x-generic.svg` | `ic_file_text.xml` |
| Markdown | `text-markdown.svg` | `ic_file_markdown.xml` |
| JSON | `application-json.svg` | `ic_file_json.xml` |
| XML | `text-xml.svg` | `ic_file_xml.xml` |
| SQL | `application-sql.svg` | `ic_file_sql.xml` |
| CSS | `text-css.svg` | `ic_file_css.xml` |
| Log | `text-x-log.svg` | `ic_file_log.xml` |
| Executable | `application-x-executable.svg` | `ic_file_executable.xml` |

### Folder Icons (from sweet-folders)

| Folder Type | SVG Source | Target Name |
|-------------|------------|-------------|
| Generic | `folder.svg` | `ic_folder.xml` |
| Documents | `folder-documents.svg` | `ic_folder_documents.xml` |
| Downloads | `folder-download.svg` | `ic_folder_downloads.xml` |
| Music | `folder-music.svg` | `ic_folder_music.xml` |
| Pictures | `folder-pictures.svg` | `ic_folder_pictures.xml` |
| Videos | `folder-videos.svg` | `ic_folder_videos.xml` |
| GitHub | `folder-github.svg` | `ic_folder_github.xml` |
| Home | `folder-home.svg` | `ic_folder_home.xml` |

## After Conversion

Once XML files are in `res/drawable/`, update the code:

### 1. Update `SftpScreen_helper.kt`

Replace Material Icons imports with drawable references:

```kotlin
import com.darkssh.client.R

internal fun getFileIcon(fileName: String): Int {
    return when (getFileType(fileName)) {
        FileType.PYTHON -> R.drawable.ic_file_python
        FileType.SHELL -> R.drawable.ic_file_shell
        FileType.JAVASCRIPT -> R.drawable.ic_file_javascript
        // ... etc
    }
}
```

### 2. Update `SftpScreen.kt`

Change Icon usage from ImageVector to drawable resource:

```kotlin
// Before
Icon(
    imageVector = icon,
    contentDescription = null,
    tint = iconTint
)

// After
Icon(
    painter = painterResource(id = iconRes),
    contentDescription = null,
    tint = iconTint
)
```

## Testing

After conversion:
1. Run `./init.sh` to verify build
2. Connect to SFTP server
3. Navigate to directory with various file types
4. Verify each file type shows correct icon
5. Check colors match file type

## Fallback Strategy

If conversion is too complex or time-consuming:
- Keep using Material Icons (current implementation)
- Icons are functional and recognizable
- Custom icons are aesthetic improvement, not critical feature

## References

- [Android Vector Drawable Documentation](https://developer.android.com/develop/ui/views/graphics/vector-drawable-resources)
- [SVG to Vector Drawable Converter](https://svg2vector.com/)
- [candy-icons Repository](https://github.com/EliverLara/candy-icons)
- [sweet-folders Repository](https://github.com/EliverLara/Sweet-folders)
