package edu.mines.csci448.lab.criminalintent.data

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.*
import java.util.*

@Dao
interface CrimeDao {

    @Query("SELECT * FROM crime")
    fun getCrimes(): DataSource.Factory<Int, Crime>

    @Query ("SELECT * FROM crime WHERE id = (:id)")
    fun getCrime(id: UUID): LiveData<Crime?>

    @Update
    fun updateCrime(crime: Crime)

    @Insert
    fun addCrime(crime: Crime)

    @Delete
    fun deleteCrime(crime: Crime)


}