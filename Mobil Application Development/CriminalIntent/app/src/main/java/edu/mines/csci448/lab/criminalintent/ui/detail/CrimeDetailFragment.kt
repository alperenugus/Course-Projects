package edu.mines.csci448.lab.criminalintent.ui.detail

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import edu.mines.csci448.lab.criminalintent.R
import edu.mines.csci448.lab.criminalintent.data.Crime
import java.util.*
import androidx.lifecycle.Observer
import android.text.format.DateFormat
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val ARG_CRIME_ID = "crime_id"
private const val REQUEST_CODE_CONTACT: Int = 1
private const val REQUIRED_CONTACTS_PERMISSION =
    android.Manifest.permission.READ_CONTACTS
private const val REQUEST_CODE_PERMISSION_READ_CONTACTS = 2

class CrimeDetailFragment : Fragment() {

    private val pickContactIntent = Intent(Intent.ACTION_PICK,
        ContactsContract.Contacts.CONTENT_URI)

    private val logTag = "448.CrimeDetailFrag"

    private lateinit var crime: Crime
    private lateinit var titleField: EditText
    private lateinit var dateButton: Button
    private lateinit var checkBox: CheckBox
    private lateinit var crimeSuspectButton: Button
    private lateinit var crimeCallButton: Button
    private lateinit var crimeReportButton: Button

    private lateinit var crimeDetailViewModel: CrimeDetailViewModel

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        Log.d(logTag, "onCreateOptionsMenu() called")

        inflater.inflate(R.menu.fragment_crime_detail, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(logTag, "onOptionsItemSelected() called")

        return when(item.itemId){
            R.id.delete_crime_menu_item -> {
                crimeDetailViewModel.deleteCrime(crime)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }


    override fun onAttach(context: Context){
        super.onAttach(context)
        Log.d(logTag, "onAttach() called")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(logTag, "onCreate() called")

        // Enable the menu
        setHasOptionsMenu(true)

        crime = Crime()

        val factory = CrimeDetailViewModelFactory(requireContext())

        crimeDetailViewModel = ViewModelProvider(this, factory)
            .get(CrimeDetailViewModel::class.java)

        val crimeId: UUID = arguments?.getSerializable(ARG_CRIME_ID) as UUID

        crimeDetailViewModel.loadCrime(crimeId)

        Log.d(logTag, "args bundle crime ID: $crimeId")

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(logTag, "onCreateView() called")

        // Set fragment layout using inflater
        val view = inflater.inflate(R.layout.fragment_detail, container, false)

        // Bind variables to the related layout
        titleField = view.findViewById(R.id.crime_title_edit_text) as EditText
        dateButton = view.findViewById(R.id.crime_date_button) as Button
        dateButton.apply {
            text = crime.date.toString()
            isEnabled = false
        }

        crimeSuspectButton = view.findViewById(R.id.crime_suspect_button)
        crimeCallButton = view.findViewById(R.id.crime_call_button)
        crimeReportButton = view.findViewById(R.id.crime_report_button)

        // Create an implicit intent to be able to share crime report
        crimeReportButton.setOnClickListener {
            var intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, generateCrimeReport())
            intent.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.crime_report_subject))
            intent = Intent.createChooser(intent, getString(R.string.send_report))
            startActivity(intent)
        }

        // Get crime suspect from the contacts
        crimeSuspectButton.setOnClickListener {
            if( !hasContactsPermission() ) {
                Log.d(logTag, "user has NOT granted permission to access contacts")
                // should we show an explanation?
                if( ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(), REQUIRED_CONTACTS_PERMISSION ) ) {
                    Log.d(logTag, "show an explanation")
                    Toast.makeText(requireContext(),
                        R.string.crime_reason_for_contacts,
                        Toast.LENGTH_LONG).show()
                } else {
                    Log.d(logTag, "no explanation needed, request permission")
                    requestPermissions(listOf(REQUIRED_CONTACTS_PERMISSION).toTypedArray(),
                        REQUEST_CODE_PERMISSION_READ_CONTACTS)
                }
            } else {
                Log.d(logTag, "user has granted permission to access contacts")
                launchContactsIntent()
            }
        }

        crimeCallButton.setOnClickListener {
            val callIntent = Intent(Intent.ACTION_DIAL)
            val phoneNumberUri = Uri.parse( "tel:${crime.suspectNumber}" )
            callIntent.data = phoneNumberUri
            startActivity( callIntent )
        }

        checkBox = view.findViewById(R.id.crime_solved_checkbox) as CheckBox

        // Check if there are any suitable activity's that can resolve the anticipated action
        crimeSuspectButton.isEnabled = requireActivity().packageManager
            .resolveActivity(pickContactIntent, PackageManager.MATCH_DEFAULT_ONLY) != null

        // Return the binded view
        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(resultCode != Activity.RESULT_OK) {
            return
        } else {
            if(requestCode == REQUEST_CODE_CONTACT) {
                if(data == null) {
                    return
                }
                val contactUri = data.data ?: return
                // specify which fields you want your query to return values for
                val queryFields = listOf(ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts._ID)
                // perform your query, the contactUri is like a "where" clause
                val cursor = requireActivity().contentResolver.query(contactUri,
                    queryFields.toTypedArray(),
                    null, null, null)
                cursor?.use {contactIter ->
                // double check that you got results
                    if(contactIter.count == 0) {
                        return
                    }
                // pull out the first column of the first row of data
                // that is the contact's name
                    contactIter.moveToFirst()
                    val suspect = contactIter.getString(0)
                    crime.suspect = suspect // set the crime's suspect field
                    crimeDetailViewModel.saveCrime(crime)// save the crime
                    crimeSuspectButton.text = suspect // change the button text

                    // pull out the second column – that is the contact's ID
                    val contactID = contactIter.getString(1)
                    val phoneURI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    val phoneNumberQueryFields = listOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER )
                    val whereClause = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
                    val phoneQueryParameters = listOf( contactID.toString() )
                    val phoneCursor = requireActivity().contentResolver
                        .query(phoneURI, phoneNumberQueryFields.toTypedArray(),
                            whereClause, phoneQueryParameters.toTypedArray(),
                            null)
                    phoneCursor?.use { phoneIter ->
                        if( phoneIter.count > 0 ) {
                            phoneIter.moveToFirst()
                            crime.suspectNumber = phoneIter.getString(0)
                            crimeCallButton.isEnabled = true
                            crimeDetailViewModel.saveCrime(crime)// save the crime
                            Log.d(logTag, "I am in phoneIter.count > 0")
                            Log.d(logTag, "Phone number: ${crime.suspectNumber.toString()}")
                        } else {
                            // no phone number found
                            crime.suspectNumber = null
                            crimeCallButton.isEnabled = false
                            Log.d(logTag, "I am in phoneIter.count < 0")
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(logTag, "onViewCreated() called")

        crimeDetailViewModel.crimeLiveData.observe(
            viewLifecycleOwner,
            Observer { crime ->
                crime?.let {
                    this.crime = crime
                    updateUI()
                }
            }
        )


    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.d(logTag, "onActivityCreated() called")
    }

    override fun onStart() {
        super.onStart()
        Log.d(logTag, "onStart() called")

        // Created a textChangedListener for the title
        val titleWatcher = object : TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // This space is intentionally left blank
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                crime.title = s.toString()
            }

            override fun afterTextChanged(s: Editable?) {
                // This one too
            }
        }

        titleField.addTextChangedListener(titleWatcher)

        checkBox.setOnClickListener {
            //crime.isSolved = true
            crime.isSolved = !crime.isSolved
        }
    }

    private fun updateUI() {
        titleField.setText(crime.title)
        dateButton.text = crime.date.toString()
        checkBox.isChecked = crime.isSolved

        if(crime.suspect != null ) {
            crimeSuspectButton.text = crime.suspect
        }

        crimeCallButton.isEnabled = crime.suspectNumber != null
        Log.d(logTag, crime.suspectNumber.toString())
    }

    companion object{
        fun newInstance(crimeId: UUID): CrimeDetailFragment{

            val args = Bundle().apply {
                putSerializable(ARG_CRIME_ID, crimeId)
            }

            return CrimeDetailFragment().apply {
                arguments = args
            }

        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(logTag, "onResume() called")
    }

    override fun onPause() {
        super.onPause()
        Log.d(logTag, "onPause() called")
    }

    override fun onStop() {
        super.onStop()
        Log.d(logTag, "onStop() called")

        crimeDetailViewModel.saveCrime(crime)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(logTag, "onDestroyView() called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(logTag, "onDestroy() called")
    }

    override fun onDetach() {
        super.onDetach()
        Log.d(logTag, "onDetach() called")
    }

    private fun generateCrimeReport(): String {
        val dateString = DateFormat.format("EEE, MMM dd", crime.date)
        val solvedString = if(crime.isSolved) {
            getString(R.string.crime_report_solved)
        } else {
            getString(R.string.crime_report_unsolved)
        }
        val suspectString = if(crime.suspect == null) {
            getString(R.string.crime_report_no_suspect)
        } else {
            getString(R.string.crime_report_suspect, crime.suspect)
        }
        return getString(R.string.crime_report, crime.title,
            dateString, solvedString, suspectString)
    }

    private fun hasContactsPermission() =
        ContextCompat.checkSelfPermission(requireContext(),
            REQUIRED_CONTACTS_PERMISSION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        if( requestCode == REQUEST_CODE_PERMISSION_READ_CONTACTS ) {
            // check if we have permission
            if( hasContactsPermission() ) {
            // permission was granted, yay! Do the task you need to
                crimeSuspectButton.isEnabled = true
                launchContactsIntent()
            } else {
            // permission denied, boo!
            // Disable the functionality that depends on this permission
                crimeSuspectButton.isEnabled = false
                Toast.makeText(activity,
                    R.string.crime_reason_for_contacts,
                    Toast.LENGTH_LONG).show()
            }        } else {
            super.onRequestPermissionsResult(requestCode,
                permissions, grantResults)
        }

    }

    private fun launchContactsIntent() {
        startActivityForResult(pickContactIntent, REQUEST_CODE_CONTACT)
    }
}