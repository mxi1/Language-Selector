package vegabobo.languageselector.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room access for the recently-used app history.
 *
 * All mutating queries are `suspend` and the observation query returns a [Flow];
 * callers must invoke them from a coroutine (the repository switches to the IO
 * dispatcher).
 */
@Dao
interface AppInfoDao {

    @Query("SELECT * FROM appinfoentity")
    suspend fun getAll(): List<AppInfoEntity>

    @Query("SELECT * FROM appinfoentity WHERE pkg = :pkg")
    suspend fun findByPkg(pkg: String): AppInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AppInfoEntity)

    @Update
    suspend fun update(entity: AppInfoEntity)

    @Query("UPDATE appinfoentity SET last_selected = NULL")
    suspend fun clearLastSelected()

    @Query("UPDATE appinfoentity SET last_selected = :lastSelected WHERE pkg = :pkg")
    suspend fun setLastSelected(pkg: String, lastSelected: Long)

    /** Keeps only the [limit] most recently selected rows, clearing the rest. */
    @Query(
        "UPDATE appinfoentity SET last_selected = NULL " +
            "WHERE pkg NOT IN (" +
            "SELECT pkg FROM appinfoentity " +
            "WHERE last_selected IS NOT NULL " +
            "ORDER BY last_selected DESC LIMIT :limit)"
    )
    suspend fun trimHistory(limit: Int)

    @Query(
        "SELECT * FROM appinfoentity " +
            "WHERE last_selected IS NOT NULL " +
            "ORDER BY last_selected DESC"
    )
    fun observeHistory(): Flow<List<AppInfoEntity>>
}
