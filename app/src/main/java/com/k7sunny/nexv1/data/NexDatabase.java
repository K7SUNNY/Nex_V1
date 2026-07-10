package com.k7sunny.nexv1.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {ChatSessionEntity.class, ChatMessageEntity.class, MemoryEntity.class}, version = 3, exportSchema = false)
public abstract class NexDatabase extends RoomDatabase {
    private static volatile NexDatabase INSTANCE;

    public abstract ChatHistoryDao chatHistoryDao();
    public abstract MemoryDao memoryDao();

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 1. Clean up duplicate memories before adding unique index
            database.execSQL("DELETE FROM memories WHERE id NOT IN (SELECT MIN(id) FROM memories GROUP BY content)");
            
            // 2. Add indices to memories table
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_memories_content` ON `memories` (`content`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_is_pinned` ON `memories` (`is_pinned`)");
        }
    };

    public static NexDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (NexDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    NexDatabase.class, "nex_database")
                            .addMigrations(MIGRATION_2_3)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
