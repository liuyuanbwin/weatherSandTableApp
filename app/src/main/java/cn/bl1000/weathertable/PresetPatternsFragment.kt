package cn.bl1000.weathertable

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import cn.bl1000.weathertable.databinding.FragmentPresetPatternsBinding

class PresetPatternsFragment : Fragment() {
    private var _binding: FragmentPresetPatternsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPresetPatternsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val patterns = listOf(
            "螺旋图案",
            "往复线扫描"
        )
        
        val adapter = PresetPatternsAdapter(patterns) { position ->
            // 点击某个图案时的操作
            val intent = Intent(activity, PatternDetailActivity::class.java)
            intent.putExtra("pattern_type", position)
            startActivity(intent)
        }
        
        binding.recyclerView.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PresetPatternsAdapter(
    private val patterns: List<String>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<PresetPatternsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preset_pattern, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = patterns[position]
        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
    }

    override fun getItemCount() = patterns.size
}