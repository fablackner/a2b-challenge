package io.github.fablackner.a2b

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.fablackner.a2b.data.A2bDatabase
import io.github.fablackner.a2b.data.Track
import io.github.fablackner.a2b.databinding.ActivityMyTracksBinding
import io.github.fablackner.a2b.databinding.TrackItemBinding
import kotlinx.coroutines.launch

/**
 * Lists the tracks created on this device. Deleting only forgets the track
 * locally — the physical tags out in the world are unaffected.
 */
class MyTracksActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMyTracksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dao = A2bDatabase.get(this).trackDao()
        val adapter = TrackAdapter { track ->
            lifecycleScope.launch {
                dao.delete(track.stamp)
                Toast.makeText(this@MyTracksActivity, R.string.track_deleted, Toast.LENGTH_SHORT).show()
            }
        }

        binding.myRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.myRecyclerView.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.observeMine().collect(adapter::submitList)
            }
        }
    }

    private class TrackAdapter(
        private val onDelete: (Track) -> Unit,
    ) : ListAdapter<Track, TrackAdapter.ViewHolder>(DIFF) {

        class ViewHolder(val binding: TrackItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = TrackItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val track = getItem(position)
            holder.binding.trackItemNameTV.text = track.name
            holder.binding.deleteIB.setOnClickListener { onDelete(track) }
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<Track>() {
                override fun areItemsTheSame(oldItem: Track, newItem: Track) =
                    oldItem.stamp == newItem.stamp

                override fun areContentsTheSame(oldItem: Track, newItem: Track) =
                    oldItem == newItem
            }
        }
    }
}
