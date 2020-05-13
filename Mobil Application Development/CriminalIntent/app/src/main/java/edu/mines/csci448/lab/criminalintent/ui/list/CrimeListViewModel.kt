package edu.mines.csci448.lab.criminalintent.ui.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.paging.PagedList
import edu.mines.csci448.lab.criminalintent.data.Crime
import edu.mines.csci448.lab.criminalintent.data.CrimeRepository

class CrimeListViewModel(private val crimeRepository: CrimeRepository): ViewModel() {

    val crimeListLiveData: LiveData<PagedList<Crime>> = crimeRepository.getCrimes()

    fun addCrime(crime: Crime){
        crimeRepository.addCrime(crime)
    }
}