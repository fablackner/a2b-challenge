package io.github.fablackner.a2b

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.fablackner.a2b.data.A2bDatabase
import io.github.fablackner.a2b.data.UserPrefs
import io.github.fablackner.a2b.databinding.ActivityMyRecordsBinding
import io.github.fablackner.a2b.databinding.RecordItemBinding
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lists every track where the user's nickname currently holds a top-three
 * place (as of the last time this device saw the finish tag).
 */
class MyRecordsActivity : AppCompatActivity() {

    private data class PersonalRecord(val stamp: Int, val trackName: String, val rank: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMyRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = RecordAdapter()
        binding.myRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.myRecyclerView.adapter = adapter

        val nickname = UserPrefs.nickname(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                A2bDatabase.get(this@MyRecordsActivity).trackDao().observeAll()
                    .map { tracks ->
                        tracks.mapNotNull { track ->
                            track.leaderboard().rankOf(nickname)?.let { rank ->
                                PersonalRecord(track.stamp, track.name, rank)
                            }
                        }
                    }
                    .collect(adapter::submitList)
            }
        }
    }

    private class RecordAdapter : ListAdapter<PersonalRecord, RecordAdapter.ViewHolder>(DIFF) {

        class ViewHolder(val binding: RecordItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = RecordItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val record = getItem(position)
            holder.binding.recordItemNameTV.text = record.trackName
            holder.binding.myRankIV.setImageResource(
                when (record.rank) {
                    1 -> R.drawable.first_place_icon
                    2 -> R.drawable.second_place_icon
                    else -> R.drawable.third_place_icon
                }
            )
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<PersonalRecord>() {
                override fun areItemsTheSame(oldItem: PersonalRecord, newItem: PersonalRecord) =
                    oldItem.stamp == newItem.stamp

                override fun areContentsTheSame(oldItem: PersonalRecord, newItem: PersonalRecord) =
                    oldItem == newItem
            }
        }
    }
}
