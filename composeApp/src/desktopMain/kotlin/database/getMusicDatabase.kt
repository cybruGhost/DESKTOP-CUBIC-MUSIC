package database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun getDesktopDatabaseBuilder(): RoomDatabase.Builder<MusicDatabase> {
    val dataHome = (if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        System.getenv("APPDATA")
    } else {
        System.getenv("XDG_DATA_HOME")
    })
        ?.takeIf { it.isNotBlank() && File(it).isAbsolute }
        ?.let(::File)
        ?: File(System.getProperty("user.home"), ".local/share")
    val dbDir = File(dataHome, "CubicMusic")
    check(dbDir.isDirectory || dbDir.mkdirs()) { "Cannot create desktop data directory: $dbDir" }
    val dbFile = File(dbDir, "rimusic.db")
    val legacyDb = File(System.getProperty("java.io.tmpdir"), "rimusic.db")
    if (!dbFile.exists() && legacyDb.isFile) {
        // Preserve libraries from earlier desktop builds that stored Room data in /tmp.
        legacyDb.copyTo(dbFile)
        listOf("-wal", "-shm").forEach { suffix ->
            File(legacyDb.path + suffix).takeIf(File::isFile)?.copyTo(File(dbFile.path + suffix))
        }
    }
    return Room.databaseBuilder<MusicDatabase>(
        name = dbFile.absolutePath,
    )
}

val MusicDatabaseDesktop: MusicDatabaseDao
    get() = getRoomDatabase(getDesktopDatabaseBuilder()).getDao()

val DB = MusicDatabaseDesktop
