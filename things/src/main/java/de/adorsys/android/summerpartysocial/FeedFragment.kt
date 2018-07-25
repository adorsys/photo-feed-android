package de.adorsys.android.summerpartysocial

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.firebase.firestore.*
import de.adorsys.android.shared.FirebaseProvider
import de.adorsys.android.shared.Post
import de.adorsys.android.shared.PostUtils
import de.adorsys.android.shared.views.BitmapUtils
import de.adorsys.android.summerpartysocial.R.id.feed_recycler_view
import kotlinx.android.synthetic.main.fragment_feed.*

class FeedFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val query = FirebaseProvider.getFeed()
        val adapter = FeedAdapter(query) { position ->
            feed_recycler_view.scrollToPosition(position)
        }
        feed_recycler_view.adapter = adapter
        val columnCount = resources.getInteger(R.integer.column_count)
        val layoutManager = GridLayoutManager(context, columnCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (position) {
                    0 -> 2
                    else -> 1
                }
            }
        }
        feed_recycler_view.layoutManager = layoutManager
    }

    override fun onStart() {
        super.onStart()
        (feed_recycler_view.adapter as FeedAdapter).startListening()
    }

    override fun onStop() {
        super.onStop()
        (feed_recycler_view.adapter as FeedAdapter).stopListening()
    }

    class FeedAdapter(private var query: Query?, private val onAdapterChangedAction: (position: Int) -> Unit) : RecyclerView.Adapter<PostViewHolder>(), EventListener<QuerySnapshot> {
        private val snapshots = mutableListOf<DocumentSnapshot>()
        private var listener: ListenerRegistration? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return PostViewHolder(inflater.inflate(R.layout.item_post, parent, false))
        }

        override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
            holder.bind(snapshots[position])
        }

        override fun onEvent(feedSnapshot: QuerySnapshot?, e: FirebaseFirestoreException?) {
            // Handle errors
            if (e != null) {
                Log.w(javaClass.canonicalName, "onEvent:error", e)
                return
            }

            if (feedSnapshot == null) {
                return
            }

            // Dispatch the event
            for (change in feedSnapshot.documentChanges) {
                when (change.type) {
                    DocumentChange.Type.ADDED -> onDocumentAdded(change)
                    DocumentChange.Type.MODIFIED -> onDocumentModified(change)
                    DocumentChange.Type.REMOVED -> onDocumentRemoved(change)
                }
            }
        }

        fun startListening() {
            if (query != null && listener == null) {
                listener = query?.addSnapshotListener(this)
            }
        }

        fun stopListening() {
            if (listener != null) {
                listener?.remove()
                listener = null
            }

            snapshots.clear()
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int {
            return snapshots.size
        }


        private fun onDocumentAdded(change: DocumentChange) {
            snapshots.add(change.newIndex, change.document)
            notifyItemInserted(change.newIndex)
            onAdapterChangedAction(change.newIndex)
        }

        private fun onDocumentModified(change: DocumentChange) {
            if (change.oldIndex == change.newIndex) {
                // Item changed but remained in same position
                snapshots[change.oldIndex] = change.document
                notifyItemChanged(change.oldIndex)
            } else {
                // Item changed and changed position
                snapshots.removeAt(change.oldIndex)
                snapshots.add(change.newIndex, change.document)
                notifyItemMoved(change.oldIndex, change.newIndex)
            }
        }

        private fun onDocumentRemoved(change: DocumentChange) {
            snapshots.removeAt(change.oldIndex)
            notifyItemRemoved(change.oldIndex)
        }
    }

    class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView = view.findViewById<ImageView>(R.id.imageView)
        private val titleTextView = view.findViewById<TextView>(R.id.titleTextView)
        private val descriptionTextView = view.findViewById<TextView>(R.id.descriptionTextView)

        fun bind(snapshot: DocumentSnapshot) {
            try {
                val post = snapshot.toObject(Post::class.java)

                if (!post?.imageReference.isNullOrEmpty()) {
                    val reference = post?.imageReference
                    FirebaseProvider.downloadImage(reference,
                            { file ->
                                val bitmap = BitmapUtils.getScaledImage(imageView.context.resources.getDimension(R.dimen.image_max_height), file.path)
                                setBitmap(bitmap)
                            },
                            {
                                Log.e("TAG_THINGS", "Could not correctly decode bitmap from $snapshot")
                            })
                } else {
                    val bitmap = post?.image?.let { PostUtils.getBitmapFromEncodedBytes(it) }
                    setBitmap(bitmap)
                }

                titleTextView?.text = titleTextView.context.getString(R.string.image_shared_by, post?.name)
                val text = post?.text
                if (text.isNullOrBlank()) {
                    descriptionTextView.visibility = View.GONE
                } else {
                    descriptionTextView.visibility = View.VISIBLE
                    descriptionTextView?.text = post?.text
                }
            } catch (e: Exception) {
                Log.e("TAG_THINGS", "Could not correctly parse post object for $snapshot")
            }
        }

        private fun setBitmap(bitmap: Bitmap?) {
            if (bitmap == null) {
                imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, R.drawable.placeholder_post))
            } else {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

}