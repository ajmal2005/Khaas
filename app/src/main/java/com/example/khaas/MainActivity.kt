package com.example.khaas

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.NotificationManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.pm.PackageManager
import android.transition.TransitionManager
import android.transition.AutoTransition
import android.view.animation.DecelerateInterpolator

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 101

    private lateinit var contactsPage: View
    private lateinit var callSetupPage: View
    private lateinit var settingsPage: View
    private lateinit var profileButton: ImageView
    private lateinit var phoneButton: ImageView
    private lateinit var settingsButton: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var callerRecyclerView: RecyclerView
    private lateinit var startCallButton: View
    private lateinit var vipSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    
    private val PREF_NAME = "KhaasPrefs"
    private val PREF_VIP_ENABLED = "vip_enabled"
    private val PREF_VIP_MAX_VOLUME = "vip_max_volume"
    
    private var selectedTimeSeconds = 30
    private var selectedContact: VipContact? = null

    private val callerAdapter = CallerAdapter(mutableListOf()) { contact ->
        selectedContact = contact
    }

    private val contactAdapter = ContactAdapter(emptyList()) { contact ->
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.vipContactDao().deleteContact(contact)
            loadContacts()
            loadContactsForSetup()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Contact deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val contactPickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                addContactFromUri(uri)
            }
        }
    }

    private var currentRingtone: android.media.Ringtone? = null

    private val ringtonePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { uri ->
                getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString("pref_app_ringtone", uri.toString()).apply()
                
                currentRingtone?.stop()
                currentRingtone = android.media.RingtoneManager.getRingtone(this, uri)
                currentRingtone?.play()
                Toast.makeText(this, "Ringtone updated. Previewing...", Toast.LENGTH_SHORT).show()
                
                Handler(Looper.getMainLooper()).postDelayed({
                    currentRingtone?.stop()
                }, 5000)
            }
        }
    }

    private var rewardedAd: com.google.android.gms.ads.rewarded.RewardedAd? = null

    private fun addContactFromUri(uri: android.net.Uri) {
        val projection = arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                val name = cursor.getString(nameIndex)
                val number = cursor.getString(numberIndex)
                
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val dao = db.vipContactDao()
                    val normalizedNumber = number.replace(Regex("[^0-9]"), "")
                    
                    if (dao.isContactVip(normalizedNumber)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Contact already exists", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        dao.insertContact(VipContact(name = name, phoneNumber = number))
                        loadContacts()
                        loadContactsForSetup()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Contact added", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val status = availability.isGooglePlayServicesAvailable(this)
        if (status != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            Toast.makeText(this, "Google Play Services not available: ${availability.getErrorString(status)}", Toast.LENGTH_LONG).show()
        }

        com.google.android.gms.ads.MobileAds.initialize(this) {}

        contactsPage = findViewById(R.id.contactsPage)
        callSetupPage = findViewById(R.id.callSetupPage)
        settingsPage = findViewById(R.id.settingsPage)
        profileButton = findViewById(R.id.profileButton)
        phoneButton = findViewById(R.id.phoneButton)
        settingsButton = findViewById(R.id.settingsButton)
        titleTextView = findViewById(R.id.titleTextView)
        
        callerRecyclerView = findViewById(R.id.callerRecyclerView)
        callerRecyclerView.layoutManager = LinearLayoutManager(this)
        callerRecyclerView.adapter = callerAdapter
        
        startCallButton = findViewById(R.id.startCallButton)
        startCallButton.setOnClickListener {
            scheduleFakeCall()
        }

        val fab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        }

        vipSwitch = findViewById(R.id.vipSwitch)
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        vipSwitch.isChecked = prefs.getBoolean(PREF_VIP_ENABLED, true)
        
        vipSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_VIP_ENABLED, isChecked).apply()
            val statusStr = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Priority Access $statusStr", Toast.LENGTH_SHORT).show()
            RingerModeReceiver.checkAndToggleService(this)
        }
        
        RingerModeReceiver.checkAndToggleService(this)

        setupHeader()
        setupTimeChips()
        loadContacts()
        checkAndRequestPermissions()

        val adViewContacts = findViewById<com.google.android.gms.ads.AdView>(R.id.adViewContacts)
        val adRequestContacts = com.google.android.gms.ads.AdRequest.Builder().build()
        adViewContacts.loadAd(adRequestContacts)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,

            Manifest.permission.READ_CONTACTS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }

        // Check for DND Access
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            AlertDialog.Builder(this)
                .setTitle("DND Access Required")
                .setMessage("To ensure VIP calls ring even in DND mode, please grant 'Do Not Disturb Access' to Khaas.")
                .setPositiveButton("Grant") { _, _ ->
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted
            } else {
                Toast.makeText(this, "Permissions required for app to function", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
        loadContactsForSetup()
    }

    private fun setupHeader() {
        profileButton.setOnClickListener { showContactsPage() }
        phoneButton.setOnClickListener { showCallSetupPage() }
        settingsButton.setOnClickListener { showSettingsPage() }
        setupSettingsLogic()
    }

    private fun showContactsPage() {
        contactsPage.visibility = View.VISIBLE
        callSetupPage.visibility = View.GONE
        settingsPage.visibility = View.GONE
        titleTextView.text = "Contacts"
        profileButton.setBackgroundResource(R.drawable.bg_avatar_circle)
        phoneButton.background = null
        settingsButton.background = null
    }

    private fun showCallSetupPage() {
        contactsPage.visibility = View.GONE
        callSetupPage.visibility = View.VISIBLE
        settingsPage.visibility = View.GONE
        titleTextView.text = "Call Setup"
        phoneButton.setBackgroundResource(R.drawable.bg_avatar_circle)
        profileButton.background = null
        settingsButton.background = null
        loadContactsForSetup()

        val adView = findViewById<com.google.android.gms.ads.AdView>(R.id.adViewCallerSetup)
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    private fun showSettingsPage() {
        contactsPage.visibility = View.GONE
        callSetupPage.visibility = View.GONE
        settingsPage.visibility = View.VISIBLE
        titleTextView.text = "Settings"
        settingsButton.setBackgroundResource(R.drawable.bg_avatar_circle)
        profileButton.background = null
        phoneButton.background = null
    }

    private fun setupTimeChips() {
        val chip15s = findViewById<TextView>(R.id.timeChip15s)
        val chip30s = findViewById<TextView>(R.id.timeChip30s)
        val chip1m = findViewById<TextView>(R.id.timeChip1m)
        val chipCustom = findViewById<TextView>(R.id.timeChipCustom)
        val customTimeContainer = findViewById<View>(R.id.customTimeContainer)
        val customTimeSlider = findViewById<com.google.android.material.slider.Slider>(R.id.customTimeSlider)
        val customTimeValue = findViewById<TextView>(R.id.customTimeValue)

        val chips = listOf(chip15s, chip30s, chip1m, chipCustom)
        
        fun updateChips(selected: TextView) {
            chips.forEach { 
                if (it == selected) {
                    it.setBackgroundResource(R.drawable.bg_chip_selected)
                    it.setTextColor(ContextCompat.getColor(this, R.color.black))
                    it.typeface = android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    it.setBackgroundResource(R.drawable.bg_chip_unselected)
                    it.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"))
                    it.typeface = android.graphics.Typeface.DEFAULT
                }
            }
            customTimeContainer.visibility = if (selected == chipCustom) View.VISIBLE else View.GONE
        }

        chip15s.setOnClickListener { selectedTimeSeconds = 15; updateChips(chip15s) }
        chip30s.setOnClickListener { selectedTimeSeconds = 30; updateChips(chip30s) }
        chip1m.setOnClickListener { selectedTimeSeconds = 60; updateChips(chip1m) }
        chipCustom.setOnClickListener { selectedTimeSeconds = customTimeSlider.value.toInt(); updateChips(chipCustom) }
        
        customTimeSlider.addOnChangeListener { _, value, _ ->
            selectedTimeSeconds = value.toInt()
            customTimeValue.text = "${selectedTimeSeconds}s"
        }
    }

    private fun setupSettingsLogic() {
        val settingsRingtone = findViewById<View>(R.id.settingsRingtone)
        val settingsVolume = findViewById<View>(R.id.settingsVolume)
        val volumeSliderContainer = findViewById<View>(R.id.volumeSliderContainer)
        val volumeSlider = findViewById<com.google.android.material.slider.Slider>(R.id.volumeSlider)
        val volumePercentage = findViewById<TextView>(R.id.volumePercentage)
        val volumeChevron = findViewById<ImageView>(R.id.volumeChevron)
        
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        settingsRingtone.setOnClickListener {
             if (rewardedAd != null) {
                rewardedAd?.show(this) {
                    openRingtonePicker()
                }
            } else {
                Toast.makeText(this, "Ad not ready yet, opening picker...", Toast.LENGTH_SHORT).show()
                openRingtonePicker()
            }
        }
        
        val savedVolume = prefs.getInt(PREF_VIP_MAX_VOLUME, 100)
        volumeSlider.value = savedVolume.toFloat()
        volumePercentage.text = "$savedVolume%"
        
        settingsVolume.setOnClickListener {
            TransitionManager.beginDelayedTransition(volumeSliderContainer.parent as ViewGroup, AutoTransition())
            if (volumeSliderContainer.visibility == View.VISIBLE) {
                volumeSliderContainer.visibility = View.GONE
                volumeChevron.animate().rotation(0f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
            } else {
                volumeSliderContainer.visibility = View.VISIBLE
                volumeChevron.animate().rotation(90f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
            }
        }
        
        volumeSlider.addOnChangeListener { _, value, _ ->
            val intValue = value.toInt()
            volumePercentage.text = "$intValue%"
            prefs.edit().putInt(PREF_VIP_MAX_VOLUME, intValue).apply()
        }

        val adView = findViewById<com.google.android.gms.ads.AdView>(R.id.adViewSettings)
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        adView.loadAd(adRequest)
        
        loadRewardedAd()

        // Notifications Info Expansion
        val settingsNotifications = findViewById<View>(R.id.settingsNotifications)
        val notificationsInfoContainer = findViewById<View>(R.id.notificationsInfoContainer)
        val notificationsChevron = findViewById<ImageView>(R.id.notificationsChevron)

        settingsNotifications.setOnClickListener {
            TransitionManager.beginDelayedTransition(notificationsInfoContainer.parent as ViewGroup, AutoTransition())
            if (notificationsInfoContainer.visibility == View.VISIBLE) {
                notificationsInfoContainer.visibility = View.GONE
                notificationsChevron.animate().rotation(0f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
            } else {
                notificationsInfoContainer.visibility = View.VISIBLE
                notificationsChevron.animate().rotation(90f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
            }
        }

        // Privacy Info Expansion
        val settingsPrivacy = findViewById<View>(R.id.settingsPrivacy)
        val privacyInfoContainer = findViewById<View>(R.id.privacyInfoContainer)
        val privacyChevron = findViewById<ImageView>(R.id.privacyChevron)

        settingsPrivacy.setOnClickListener {
            TransitionManager.beginDelayedTransition(privacyInfoContainer.parent as ViewGroup, AutoTransition())
            if (privacyInfoContainer.visibility == View.VISIBLE) {
                privacyInfoContainer.visibility = View.GONE
                privacyChevron.animate().rotation(0f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
            } else {
                privacyInfoContainer.visibility = View.VISIBLE
                privacyChevron.animate().rotation(90f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
            }
        }
    }

    private fun loadRewardedAd() {
        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
        com.google.android.gms.ads.rewarded.RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", adRequest, object : com.google.android.gms.ads.rewarded.RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: com.google.android.gms.ads.LoadAdError) {
                rewardedAd = null
            }
            override fun onAdLoaded(ad: com.google.android.gms.ads.rewarded.RewardedAd) {
                rewardedAd = ad
                rewardedAd?.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        loadRewardedAd()
                    }
                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        rewardedAd = null
                        openRingtonePicker()
                    }
                }
            }
        })
    }

    private fun openRingtonePicker() {
        val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_RINGTONE)
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone")
        
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentRingtone = prefs.getString("pref_app_ringtone", null)
        val existingUri = if (currentRingtone != null) android.net.Uri.parse(currentRingtone) else android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
        
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
        ringtonePickerLauncher.launch(intent)
    }

    private fun loadContacts() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        if (recyclerView.adapter == null) {
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = contactAdapter
        }

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val contacts = db.vipContactDao().getAllContacts()
            withContext(Dispatchers.Main) {
                contactAdapter.updateContacts(contacts)
            }
        }
    }

    private fun loadContactsForSetup() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dbContacts = db.vipContactDao().getAllContacts()
            
            val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            
            val allContacts = mutableListOf<VipContact>()
            
            // Load Mom (ID -2)
            val momName = prefs.getString("pref_mom_name", "Mom") ?: "Mom"
            val momNumber = prefs.getString("pref_mom_number", "9876543210") ?: "9876543210"
            allContacts.add(VipContact(id = -2, name = momName, phoneNumber = momNumber))
            
            allContacts.addAll(dbContacts)
            
            // Load Custom Contact (ID -1)
            // Always show default title/subtitle for Custom Contact in the list
            allContacts.add(VipContact(id = -1, name = "Custom Contact", phoneNumber = "Tap to enter details"))
            
            withContext(Dispatchers.Main) {
                callerAdapter.updateContacts(allContacts)
            }
        }
    }



    private fun scheduleFakeCall() {
        val contact = selectedContact
        if (contact == null) {
            Toast.makeText(this, "Please select a caller", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Call scheduled in $selectedTimeSeconds seconds", Toast.LENGTH_SHORT).show()

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, FakeCallActivity::class.java).apply {
                putExtra("EXTRA_NAME", contact.name)
                putExtra("EXTRA_NUMBER", contact.phoneNumber)
            }
            startActivity(intent)
        }, selectedTimeSeconds * 1000L)
    }

    inner class CallerAdapter(
        private var contacts: MutableList<VipContact>,
        private val onContactSelected: (VipContact) -> Unit
    ) : RecyclerView.Adapter<CallerAdapter.CallerViewHolder>() {

        private var selectedPosition = -1

        inner class CallerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
            val phoneTextView: TextView = itemView.findViewById(R.id.phoneTextView)
            val avatarImageView: ImageView = itemView.findViewById(R.id.avatarImageView)
            val chevronImageView: ImageView = itemView.findViewById(R.id.chevronImageView)
            val customInputsContainer: View = itemView.findViewById(R.id.customInputsContainer)
            val customNameInput: android.widget.EditText = itemView.findViewById(R.id.customNameInput)
            val customNumberInput: android.widget.EditText = itemView.findViewById(R.id.customNumberInput)
            val itemContainer: View = itemView.findViewById(R.id.itemContainer)
            
            // TextWatchers need to be removed to avoid recycling issues
            private var nameWatcher: android.text.TextWatcher? = null
            private var numberWatcher: android.text.TextWatcher? = null

            fun bind(contact: VipContact, position: Int) {
                nameTextView.text = contact.name
                phoneTextView.text = contact.phoneNumber
                
                // Clear previous listeners
                customNameInput.onFocusChangeListener = null
                customNumberInput.onFocusChangeListener = null
                
                // Consume clicks on inputs to prevent triggering the row click listener
                customInputsContainer.setOnClickListener { }
                customNameInput.setOnClickListener { }
                customNumberInput.setOnClickListener { }
                
                if (nameWatcher != null) customNameInput.removeTextChangedListener(nameWatcher)
                if (numberWatcher != null) customNumberInput.removeTextChangedListener(numberWatcher)

                val isEditable = contact.id == -1 || contact.id == -2

                if (isEditable) {
                    chevronImageView.visibility = View.VISIBLE
                } else {
                    chevronImageView.visibility = View.GONE
                    customInputsContainer.visibility = View.GONE
                }

                val isExpanded = position == selectedPosition
                
                if (isExpanded) {
                     itemContainer.setBackgroundResource(R.drawable.bg_glow_border)
                     if (isEditable) {
                         customInputsContainer.visibility = View.VISIBLE
                         chevronImageView.rotation = 90f
                         
                         // Pre-fill inputs
                         // For Custom Contact (ID -1), if it's the placeholder "Custom Contact", show empty or saved?
                         // We loaded the saved values into the contact object in loadContactsForSetup, so we can use those.
                         // But for "Custom Contact" placeholder, we might want to show empty fields if it's the first time.
                         // Let's rely on SharedPreferences directly for the inputs to be sure.
                         val prefs = itemView.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                         val savedNameKey = if (contact.id == -2) "pref_mom_name" else "pref_custom_name"
                         val savedNumberKey = if (contact.id == -2) "pref_mom_number" else "pref_custom_number"
                         val defaultName = if (contact.id == -2) "Mom" else ""
                         val defaultNumber = if (contact.id == -2) "9876543210" else ""
                         
                         val currentName = prefs.getString(savedNameKey, defaultName) ?: defaultName
                         val currentNumber = prefs.getString(savedNumberKey, defaultNumber) ?: defaultNumber
                         
                         if (customNameInput.text.toString() != currentName) customNameInput.setText(currentName)
                         if (customNumberInput.text.toString() != currentNumber) customNumberInput.setText(currentNumber)
                         
                         // Setup Watchers
                         nameWatcher = object : android.text.TextWatcher {
                             override fun afterTextChanged(s: android.text.Editable?) {
                                 val newName = s.toString()
                                 prefs.edit().putString(savedNameKey, newName).apply()
                                 
                                 val currentNum = customNumberInput.text.toString()
                                 val updatedContact = contact.copy(name = newName, phoneNumber = if (currentNum.isBlank()) "Unknown" else currentNum)
                                 
                                 if (contact.id == -2) { // Only update preview for Mom
                                     // Update UI Preview directly without notifying adapter to avoid focus loss
                                     nameTextView.text = newName
                                     // Update Data List
                                     contacts[position] = updatedContact
                                 } else {
                                     // For others (Custom Contact), just update data
                                     contacts[position] = updatedContact
                                 }
                                 
                                 // Update selectedContact live
                                 selectedContact = updatedContact
                             }
                             override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                             override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                         }
                         customNameInput.addTextChangedListener(nameWatcher)
                         
                         numberWatcher = object : android.text.TextWatcher {
                             override fun afterTextChanged(s: android.text.Editable?) {
                                 val newNumber = s.toString()
                                 prefs.edit().putString(savedNumberKey, newNumber).apply()
                                 
                                 val currentNameVal = customNameInput.text.toString()
                                 val updatedContact = contact.copy(name = currentNameVal, phoneNumber = if (newNumber.isBlank()) "Unknown" else newNumber)
                                 
                                 if (contact.id == -2) { // Only update preview for Mom
                                     // Update UI Preview directly
                                     phoneTextView.text = newNumber
                                     // Update Data List
                                     contacts[position] = updatedContact
                                 } else {
                                     contacts[position] = updatedContact
                                 }
                                 
                                 // Update selectedContact live
                                 selectedContact = updatedContact
                             }
                             override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                             override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                         }
                         customNumberInput.addTextChangedListener(numberWatcher)
                         
                         // Also set selectedContact immediately upon expansion
                         selectedContact = VipContact(id = contact.id, name = currentName, phoneNumber = if (currentNumber.isBlank()) "Unknown" else currentNumber)
                     }
                } else {
                     itemContainer.background = null
                     if (isEditable) {
                         customInputsContainer.visibility = View.GONE
                         chevronImageView.rotation = 0f
                     }
                }

                itemContainer.setOnClickListener {
                    val previousSelected = selectedPosition
                    
                    if (selectedPosition == adapterPosition) {
                        // Toggle Collapse
                        selectedPosition = -1
                    } else {
                        // Expand
                        selectedPosition = adapterPosition
                    }
                    
                    notifyItemChanged(previousSelected)
                    notifyItemChanged(adapterPosition) // Update new (or same) position
                    
                    if (!isEditable) {
                        onContactSelected(contact)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallerViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_caller_selection, parent, false)
            return CallerViewHolder(view)
        }

        override fun onBindViewHolder(holder: CallerViewHolder, position: Int) {
            holder.bind(contacts[position], position)
        }

        override fun getItemCount() = contacts.size

        fun updateContacts(newContacts: List<VipContact>) {
            contacts = newContacts.toMutableList()
            notifyDataSetChanged()
        }
    }

    inner class ContactAdapter(
        private var contacts: List<VipContact>,
        private val onDeleteContact: (VipContact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

        inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
            val phoneTextView: TextView = itemView.findViewById(R.id.phoneTextView)
            val menuButton: android.widget.ImageButton = itemView.findViewById(R.id.menuButton)
            
            fun bind(contact: VipContact) {
                nameTextView.text = contact.name
                phoneTextView.text = contact.phoneNumber
                
                menuButton.setOnClickListener { view ->
                    val popup = android.widget.PopupMenu(view.context, view)
                    popup.menu.add("Delete")
                    popup.setOnMenuItemClickListener { item ->
                        if (item.title == "Delete") {
                            onDeleteContact(contact)
                            true
                        } else {
                            false
                        }
                    }
                    popup.show()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vip_contact, parent, false)
            return ContactViewHolder(view)
        }

        override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
            holder.bind(contacts[position])
        }

        override fun getItemCount() = contacts.size

        fun updateContacts(newContacts: List<VipContact>) {
            contacts = newContacts
            notifyDataSetChanged()
        }
    }
}
