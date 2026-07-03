package com.k7sunny.nexv1.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ChatSessionEntity.class, ChatMessageEntity.class, MemoryEntity.class}, version = 2, exportSchema = false)
public abstract class NexDatabase extends RoomDatabase {
    private static volatile NexDatabase INSTANCE;

    public abstract ChatHistoryDao chatHistoryDao();
    public abstract MemoryDao memoryDao();

    public static NexDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (NexDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    NexDatabase.class, "nex_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
