package tus1327.coco

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.drive.Drive
import com.google.android.gms.drive.DriveFile
import com.google.android.gms.drive.DriveResourceClient
import com.google.android.gms.drive.query.*
import com.google.android.gms.tasks.Tasks
import kotlinx.android.synthetic.main.clip_item.view.*
import kotlinx.android.synthetic.main.fragment_item.view.*
import timber.log.Timber
import java.io.InputStreamReader
import java.lang.Exception
import kotlin.concurrent.thread

class HomeFragment : Fragment() {

    companion object {
        const val ARG_COLUMN_COUNT = "column-count"

        @JvmStatic
        fun newInstance(columnCount: Int) =
                HomeFragment().apply {
                    arguments = Bundle().apply { putInt(ARG_COLUMN_COUNT, columnCount) }
                }
    }

    private var columnCount = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
    }

    private val myItemRecyclerViewAdapter = MyItemRecyclerViewAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_item_list, container, false)
        if (view is RecyclerView) {
            with(view) {
                layoutManager = when {
                    columnCount <= 1 -> LinearLayoutManager(context)
                    else -> GridLayoutManager(context, columnCount)
                }
                adapter = myItemRecyclerViewAdapter
            }
        }
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        loadFileList()
    }

    private fun getDriveResourceClient(): DriveResourceClient? {
        return GoogleSignIn.getLastSignedInAccount(activity)?.let {
            Drive.getDriveResourceClient(activity!!, it)
        }
    }

    private fun showError(it: Exception) {
        Timber.e("showError $it")
        Toast.makeText(activity, it.message, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("BinaryOperationInTimber")
    private fun loadFileList() {
        getDriveResourceClient()?.apply {
            val q = Query.Builder()
                    .addFilter(Filters.eq(SearchableField.TITLE, FOLDER_NAME))
                    .addFilter(Filters.eq(SearchableField.TRASHED, false))
                    .addFilter(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"))
                    .setSortOrder(SortOrder.Builder().addSortDescending(SortableField.TITLE).build())
                    .build()

            query(q)
                    .addOnSuccessListener { metas ->
                        if (metas.count > 0) {
                            listChildren(metas[0].driveId.asDriveFolder())
                                    .addOnSuccessListener { metas2 ->
                                        thread {
                                            metas2.map { meta ->
                                                val items = meta.driveId.asDriveFile().let { driveFile ->
                                                    val driveContents = Tasks.await(openFile(driveFile, DriveFile.MODE_READ_ONLY))
                                                    InputStreamReader(driveContents.inputStream).use { stream ->
                                                        stream.readLines()
                                                                .map { Item(it.substring(0, 14), it.substring(16)) }
                                                                .toList()
                                                    }
                                                }

                                                Monthly(meta.title.replace(".txt", ""), items)
                                            }.toList().let {
                                                activity?.runOnUiThread {
                                                    myItemRecyclerViewAdapter.setData(it)
                                                    myItemRecyclerViewAdapter.notifyDataSetChanged()
                                                }
                                            }
                                        }

                                    }
                                    .addOnFailureListener {
                                        showError(it)
                                    }
                        } else {
                            showError(Exception("NO CONTENT"))
                        }
                    }
                    .addOnFailureListener {
                        showError(it)
                    }

        }
    }

    class MyItemRecyclerViewAdapter(private val mValues : MutableList<Monthly> = mutableListOf()) : RecyclerView.Adapter<MyItemRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val monthly = mValues[position]

            holder.mIdView.text = monthly.getDisplayDate()
            holder.mContentView.adapter = ClipRecyclerViewAdapter(monthly.items)
            holder.mContentView.isNestedScrollingEnabled = false
        }

        override fun getItemCount(): Int {
            return mValues.size
        }

        fun setData(it: List<Monthly>) {
            mValues.clear()
            mValues.addAll(it)
        }

        inner class ViewHolder(itemVeiw: View) : RecyclerView.ViewHolder(itemVeiw) {
            val mIdView: TextView = itemVeiw.title
            val mContentView: RecyclerView = itemVeiw.recyclerView
        }
    }

    data class Monthly(private val month: String, val items : List<Item> = listOf()) {
        fun getDisplayDate() : String {
            return FILE_DISPLAY_FORMAT.format(FILE_FORMAT.parse(month))
        }
    }

    data class Item(val date : String, val clip : String) {
        fun getDisplayDate() : String {
            return LINE_DISPLAY_FORMAT.format(LINE_FORMAT.parse(date))
        }
    }

    class ClipRecyclerViewAdapter(private val mValues : List<Item> = listOf()) : RecyclerView.Adapter<ClipRecyclerViewAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.clip_item, parent, false))
        }

        override fun getItemCount(): Int {
            return mValues.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            with(mValues[position]) {
                holder.dateView.text = getDisplayDate()
                holder.contentView.text = clip
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val dateView: TextView = itemView.date
            val contentView: TextView = itemView.content
        }

    }

}
