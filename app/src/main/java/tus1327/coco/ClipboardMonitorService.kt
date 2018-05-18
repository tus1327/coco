package tus1327.coco

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.drive.*
import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.Query
import com.google.android.gms.drive.query.SearchableField
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Exception
import java.util.*


class ClipboardMonitorService : Service(), ClipboardManager.OnPrimaryClipChangedListener {

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, ClipboardMonitorService::class.java))
        }
    }

    private var clipboardManager: ClipboardManager? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Preferences(this, PREF_NAME)

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        clipboardManager?.addPrimaryClipChangedListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager?.removePrimaryClipChangedListener(this)
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onPrimaryClipChanged() {
        clipboardManager?.primaryClip?.let { clip ->
            for (i in 0 until clip.itemCount) {
                Timber.d("Clip Item $i : ${clip.getItemAt(i)}")
                writeClip(clip.getItemAt(i))
            }
        }
    }

    private fun newMessage(message: String) {
        Timber.d("newMessage : $message")

        val currentTime = Calendar.getInstance().time
        val fileName = "${FILE_FORMAT.format(currentTime)}.txt"
        val formatted = "${LINE_FORMAT.format(currentTime)}, $message\n"

        val success: (DriveResourceClient, DriveFile) -> Unit = { drive, file ->
            drive.apply {
                openFile(file, DriveFile.MODE_READ_WRITE)
                        .continueWithTask { task ->
                            val driveContents = task.result

                            val pfd = driveContents.parcelFileDescriptor
                            var bytesToSkip = pfd.statSize

                            FileInputStream(pfd.fileDescriptor).use { inputStream ->
                                while (bytesToSkip > 0) {
                                    val skipped = inputStream.skip(bytesToSkip)
                                    bytesToSkip -= skipped
                                }
                            }

                            FileOutputStream(pfd.fileDescriptor).use({ out -> out.write(formatted.toByteArray()) })

                            val changeSet = MetadataChangeSet.Builder().setLastViewedByMeDate(currentTime).build()
                            commitContents(driveContents, changeSet)
                        }
                        .addOnSuccessListener {
                            Timber.d("new message success !: $message")
                        }
                        .addOnFailureListener {
                            showError(it)
                        }
            }
        }

        val newFile: (DriveResourceClient, DriveFolder) -> Unit = { drive, folder ->
            drive.apply {
                createContents()
                        .continueWithTask { task ->
                            val driveContents = task.result

                            val pfd = driveContents.parcelFileDescriptor
                            FileOutputStream(pfd.fileDescriptor).use { out -> out.write(formatted.toByteArray()) }

                            val changeSet = MetadataChangeSet.Builder()
                                    .setTitle(fileName)
                                    .setMimeType("text/plain")
                                    .build()
                            createFile(folder, changeSet, driveContents)
                        }
                        .addOnSuccessListener {
                            Timber.d("createFile $it")
                        }
                        .addOnFailureListener {
                            showError(it)
                        }
            }
        }

        val newFolder: (DriveResourceClient) -> Unit = { drive ->
            drive.apply {
                val q = Query.Builder()
                        .addFilter(Filters.eq(SearchableField.TITLE, FOLDER_NAME))
                        .addFilter(Filters.eq(SearchableField.TRASHED, false))
                        .addFilter(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"))
                        .build()

                query(q).addOnSuccessListener { metas ->
                    if (metas.count == 0) {
                        rootFolder.continueWithTask {
                            val changeSet = MetadataChangeSet.Builder()
                                    .setTitle(FOLDER_NAME)
                                    .setMimeType(DriveFolder.MIME_TYPE)
                                    .build()

                            createFolder(it.result, changeSet)
                                    .addOnSuccessListener { Timber.d("newFolder $it") }
                                    .addOnFailureListener { showError(it) }

                        }.addOnSuccessListener {
                            newFile(drive, it)
                        }.addOnFailureListener {
                            showError(it)
                        }
                    } else {
                        newFile(drive, metas[0].driveId.asDriveFolder())
                    }
                }.addOnFailureListener {
                    showError(it)
                }
            }
        }


        getDriveResourceClient()?.apply {
            val q = Query.Builder()
                    .addFilter(Filters.eq(SearchableField.TITLE, fileName))
                    .addFilter(Filters.eq(SearchableField.TRASHED, false))
                    .build()

            query(q)
                    .addOnSuccessListener { metas ->
                        if (metas.count > 0) {
                            metas[0]?.driveId
                                    ?.asDriveFile()
                                    ?.let { success(this, it) }
                                    ?: run { newFolder(this) }
                        } else {
                            newFolder(this)
                        }
                    }
                    .addOnFailureListener {
                        showError(it)
                    }
        }
    }

    private fun showError(it: Exception) {
        Timber.tag("CM").w(it)
    }

    var lastItem: String? = null

    private fun writeClip(clip: ClipData.Item) {
        val message = clip.coerceToText(this).toString().replace("\n", " ")
        message.takeIf { message != lastItem && message.isNotBlank() }
                ?.let {
                    lastItem = it
                    newMessage(it)
                }
    }

    private fun getDriveResourceClient(): DriveResourceClient? {
        return GoogleSignIn.getLastSignedInAccount(this)?.let {
            Drive.getDriveResourceClient(this, it)
        }
    }

    private lateinit var prefs: Preferences
}