package com.darkssh.client.ui.screens

enum class FileType {
    ARCHIVE,    // zip, tar, gz, 7z, rar, etc
    ISO,        // iso, img disk images
    IMAGE,      // png, jpg, etc
    VIDEO,      // mp4, mkv, etc
    AUDIO,      // mp3, wav, etc
    PDF,        // pdf
    PYTHON,     // .py
    SHELL,      // .sh, .bash, etc
    JAVASCRIPT, // .js, .jsx, .ts, .tsx
    JAVA,       // .java, .jar
    KOTLIN,     // .kt, .kts
    PHP,        // .php
    RUBY,       // .rb
    GO,         // .go
    RUST,       // .rs
    CPP,        // .cpp, .c, .h
    CSHARP,     // .cs
    EXECUTABLE, // .apk, .deb, .rpm, .exe
    CONFIG,     // json, yaml, xml, ini, etc
    SQL,        // .sql
    CSS,        // .css, .scss
    MARKDOWN,   // .md
    LOG,        // .log
    TEXT,       // .txt
    SECURITY,   // keys, certs
    GENERIC     // unknown
}

// Helper function to get file type based on extension
internal fun getFileType(fileName: String): FileType {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    
    return when (extension) {
        // Python
        "py", "pyc", "pyo", "pyd" -> FileType.PYTHON
        
        // Shell scripts
        "sh", "bash", "zsh", "fish", "ksh", "csh", "tcsh" -> FileType.SHELL
        
        // JavaScript/TypeScript
        "js", "jsx", "mjs", "cjs", "ts", "tsx" -> FileType.JAVASCRIPT
        
        // Java
        "java", "jar", "class" -> FileType.JAVA
        
        // Kotlin
        "kt", "kts" -> FileType.KOTLIN
        
        // PHP
        "php", "phtml", "php3", "php4", "php5" -> FileType.PHP
        
        // Ruby
        "rb", "erb", "rake" -> FileType.RUBY
        
        // Go
        "go" -> FileType.GO
        
        // Rust
        "rs" -> FileType.RUST
        
        // C/C++
        "c", "cpp", "cc", "cxx", "h", "hpp", "hxx" -> FileType.CPP
        
        // C#
        "cs" -> FileType.CSHARP
        
        // Executables/Packages
        "apk", "aab", "deb", "rpm", "exe", "msi", "dmg", "app" -> FileType.EXECUTABLE
        
        // Archives/Compressed
        "zip", "tar", "gz", "bz2", "xz", "7z", "rar", "tgz", "tbz2", 
        "txz", "tar.gz", "tar.bz2", "tar.xz" -> FileType.ARCHIVE
        
        // ISO/Disk Images
        "iso", "img", "dmg", "vdi", "vmdk", "qcow2" -> FileType.ISO
        
        // Images
        "png", "jpg", "jpeg", "jpe", "jpf", "jps", "jfif", "gif", "apng", 
        "bmp", "wbmp", "webp", "tif", "tiff", "pjp", "pjpeg", "jxl", 
        "ico", "cur", "tga", "svg", "heic", "heif", "avif" -> FileType.IMAGE
        
        // Videos
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", 
        "mpg", "mpeg", "3gp", "ogv" -> FileType.VIDEO
        
        // Audio
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", 
        "ape", "alac", "aiff" -> FileType.AUDIO
        
        // PDF
        "pdf" -> FileType.PDF
        
        // Config files (JSON, YAML, XML, TOML, INI)
        "json", "yaml", "yml", "toml", "xml", "conf", "config", 
        "ini", "cfg", "properties", "env", "editorconfig", "gitignore", 
        "dockerignore" -> FileType.CONFIG
        
        // SQL
        "sql", "db", "sqlite", "sqlite3" -> FileType.SQL
        
        // CSS
        "css", "scss", "sass", "less" -> FileType.CSS
        
        // Markdown
        "md", "markdown", "rst", "adoc" -> FileType.MARKDOWN
        
        // Log files
        "log" -> FileType.LOG
        
        // Text files
        "txt" -> FileType.TEXT
        
        // Security/Keys
        "pem", "key", "pub", "crt", "cer", "p12", "pfx", "jks", 
        "keystore" -> FileType.SECURITY
        
        // Generic file
        else -> FileType.GENERIC
    }
}

// Helper function to get file icon path from assets
internal fun getFileIconPath(fileName: String): String {
    return when (getFileType(fileName)) {
        FileType.PYTHON -> "file:///android_asset/icons/ic_file_python.svg"
        FileType.SHELL -> "file:///android_asset/icons/ic_file_shell.svg"
        FileType.JAVASCRIPT -> "file:///android_asset/icons/ic_file_javascript.svg"
        FileType.JAVA -> "file:///android_asset/icons/ic_file_java.svg"
        FileType.KOTLIN -> "file:///android_asset/icons/ic_file_java.svg" // Use Java icon for Kotlin
        FileType.PHP -> "file:///android_asset/icons/ic_file_text.svg" // Fallback to text
        FileType.RUBY -> "file:///android_asset/icons/ic_file_text.svg" // Fallback to text
        FileType.GO -> "file:///android_asset/icons/ic_file_text.svg" // Fallback to text
        FileType.RUST -> "file:///android_asset/icons/ic_file_text.svg" // Fallback to text
        FileType.CPP -> "file:///android_asset/icons/ic_file_text.svg" // Fallback to text
        FileType.CSHARP -> "file:///android_asset/icons/ic_file_text.svg" // Fallback to text
        FileType.EXECUTABLE -> "file:///android_asset/icons/ic_file_archive.svg" // Use archive icon
        FileType.ARCHIVE -> "file:///android_asset/icons/ic_file_archive.svg"
        FileType.ISO -> "file:///android_asset/icons/ic_file_iso.svg"
        FileType.IMAGE -> "file:///android_asset/icons/ic_file_image.svg"
        FileType.VIDEO -> "file:///android_asset/icons/ic_file_video.svg"
        FileType.AUDIO -> "file:///android_asset/icons/ic_file_audio.svg"
        FileType.PDF -> "file:///android_asset/icons/ic_file_pdf.svg"
        FileType.CONFIG -> "file:///android_asset/icons/ic_file_json.svg"
        FileType.SQL -> "file:///android_asset/icons/ic_file_sql.svg"
        FileType.CSS -> "file:///android_asset/icons/ic_file_text.svg" // Fallback to text
        FileType.MARKDOWN -> "file:///android_asset/icons/ic_file_text.svg"
        FileType.LOG -> "file:///android_asset/icons/ic_file_text.svg"
        FileType.TEXT -> "file:///android_asset/icons/ic_file_text.svg"
        FileType.SECURITY -> "file:///android_asset/icons/ic_file_text.svg" // Fallback to text
        FileType.GENERIC -> "file:///android_asset/icons/ic_file_text.svg"
    }
}

// Helper function to get folder icon path
internal fun getFolderIconPath(folderName: String): String {
    // TODO: Add smart folder detection (Music, Pictures, Videos, etc.)
    return when (folderName.lowercase()) {
        "home", "~" -> "file:///android_asset/icons/ic_folder_home.svg"
        else -> "file:///android_asset/icons/ic_folder.svg"
    }
}
