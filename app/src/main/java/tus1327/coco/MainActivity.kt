package tus1327.coco

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.drive.Drive
import com.google.android.gms.drive.DriveClient
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber


class MainActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_CODE_SIGN_IN = 1024

    }

    private lateinit var prefs: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ClipboardMonitorService.start(this)

        prefs = Preferences(this, PREF_NAME)

        val adapter = ViewPagerAdapter(supportFragmentManager, listOf(
                HomeFragment.newInstance(1),
                DashBoardFragment.newInstance("Hello World, ", "DashBoard"),
                NotificationFragment.newInstance("Noti", "BlahBlah")
        ))

        mainViewPager.adapter = adapter
        mainViewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            var prevMenuItem: MenuItem = navigation.menu.getItem(0)
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageSelected(position: Int) {
                prevMenuItem.isChecked = false
                val nextMenuItem = navigation.menu.getItem(position)
                nextMenuItem.isChecked = true
                prevMenuItem = nextMenuItem
            }
        })

        navigation.setOnNavigationItemSelectedListener(this::navigationItemSelected)

        val toolbar = toolbar as Toolbar
        setSupportActionBar(toolbar)


        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(Drive.SCOPE_FILE, Drive.SCOPE_APPFOLDER)
                .build()

        val googleSignInClient = GoogleSignIn.getClient(this, signInOptions)
        GoogleSignIn.getLastSignedInAccount(this)?.let {
            googleSignInClient.silentSignIn()
                    .addOnSuccessListener { updateAccountView(it) }
                    .addOnFailureListener {

                    }
        }

        val avatar = toolbar.findViewById<ImageView>(R.id.avatar)
        avatar.setOnClickListener {
            startActivityForResult(googleSignInClient.signInIntent, REQUEST_CODE_SIGN_IN)
        }
    }

    private fun navigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.navigation_home -> 0
            R.id.navigation_dashboard -> 1
            R.id.navigation_notifications -> 2
            else -> null
        }?.let {
            mainViewPager.currentItem = it
            return true
        }
        return false
    }


    private fun updateAccountView(it: GoogleSignInAccount) {
        val toolbar = toolbar as Toolbar
        with(toolbar) {
            val profileImageView = toolbar.findViewById<ImageView>(R.id.avatar)
            timber.log.Timber.i("Update view with sign-in account. $it => $profileImageView")
            it.photoUrl?.apply {
                Timber.d("Picasso load $this")
                Picasso.get().load(this).into(profileImageView)
            }
            profileImageView.setOnClickListener(null)
        }

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_SIGN_IN -> {
                Timber.i("Sign-in request code.")

                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Timber.i("Signed in successfully.")
                        GoogleSignIn.getLastSignedInAccount(this)?.let { updateAccountView(it) }
                    }
                    else -> {
                        val message = String.format("Unable to sign in, result code %d", resultCode)
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    private fun getDriveClient(): DriveClient? {
        return GoogleSignIn.getLastSignedInAccount(this)?.let {
            Drive.getDriveClient(this, it)
        }
    }

    inner class ViewPagerAdapter(fragmentManager: FragmentManager, private val fragments: List<Fragment> = emptyList()) : FragmentPagerAdapter(fragmentManager) {
        override fun getItem(position: Int) = fragments[position]
        override fun getCount() = fragments.size
    }
}
