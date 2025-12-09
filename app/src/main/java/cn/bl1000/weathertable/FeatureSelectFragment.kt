package cn.bl1000.weathertable

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import cn.bl1000.weathertable.databinding.FragmentFeatureSelectBinding

/**
 * 功能选择页面Fragment
 * 提供两个选项：1. 图片处理功能 2. 一笔画功能
 */
class FeatureSelectFragment : Fragment() {

    private var _binding: FragmentFeatureSelectBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeatureSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置按钮点击事件
        binding.btnImageProcessing.setOnClickListener {
            // 跳转到原FirstFragment（图片处理功能）
            findNavController().navigate(R.id.action_FeatureSelectFragment_to_FirstFragment)
        }

        binding.btnOneStrokePath.setOnClickListener {
            // 跳转到一笔画功能页面（暂时跳转到FirstFragment，后续可以创建专门的一笔画页面）
            findNavController().navigate(R.id.action_FeatureSelectFragment_to_OneStrokePathFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}