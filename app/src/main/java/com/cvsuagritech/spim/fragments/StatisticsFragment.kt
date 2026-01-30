package com.cvsuagritech.spim.fragments

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.database.PestDatabaseHelper
import com.cvsuagritech.spim.databinding.FragmentStatisticsBinding
import com.cvsuagritech.spim.models.HistoryItem
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    private lateinit var databaseHelper: PestDatabaseHelper
    private var allHistoryItems: List<HistoryItem> = emptyList()

    private val beneficialInsects = listOf("pygmygrasshopper", "pygmy grasshopper")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        databaseHelper = PestDatabaseHelper(requireContext())
        
        setupTabLayout()
        loadData()
    }

    private fun setupTabLayout() {
        binding.tabLayoutTimeframe.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val timeframe = tab?.text.toString()
                // Timeframe comparison is done against string resources in updateTrendChart
                updateTrendChart(timeframe)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            allHistoryItems = databaseHelper.getAllHistoryItems()
            
            val pestCounts = mutableMapOf<String, Int>()
            var totalPests = 0
            var totalBeneficial = 0
            
            allHistoryItems.forEach { item ->
                when (item) {
                    is HistoryItem.IdentificationItem -> {
                        val name = item.insectName.lowercase()
                        if (isBeneficial(name)) {
                            totalBeneficial += 1
                        } else {
                            totalPests += 1
                        }
                        pestCounts[item.insectName] = pestCounts.getOrDefault(item.insectName, 0) + 1
                    }
                    is HistoryItem.CountItem -> {
                        item.getBreakdownMap().forEach { (name, count) ->
                            if (isBeneficial(name.lowercase())) {
                                totalBeneficial += count
                            } else {
                                totalPests += count
                            }
                            pestCounts[name] = pestCounts.getOrDefault(name, 0) + count
                        }
                    }
                }
            }
            
            val mostCommon = pestCounts.maxByOrNull { it.value }?.key ?: "N/A"

            withContext(Dispatchers.Main) {
                binding.tvTotalPests.text = totalPests.toString()
                binding.tvTotalBeneficial.text = totalBeneficial.toString()
                binding.tvMostCommonPest.text = mostCommon
                
                setupHorizontalBarChart(pestCounts)
                updateTrendChart(getString(R.string.stats_time_day))
            }
        }
    }

    private fun isBeneficial(name: String): Boolean {
        return beneficialInsects.any { name.contains(it) }
    }

    private fun setupHorizontalBarChart(data: Map<String, Int>) {
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        val colors = mutableListOf<Int>()
        
        val textColor = getThemeColor(android.R.attr.textColorPrimary)
        val primaryGreen = ContextCompat.getColor(requireContext(), R.color.primary_green)
        val errorRed = ContextCompat.getColor(requireContext(), R.color.error_red)

        // Show top 5 pests
        data.entries.sortedByDescending { it.value }.take(5).forEachIndexed { index, entry ->
            entries.add(BarEntry(index.toFloat(), entry.value.toFloat()))
            labels.add(entry.key)
            if (isBeneficial(entry.key.lowercase())) {
                colors.add(primaryGreen)
            } else {
                colors.add(errorRed)
            }
        }

        val dataSet = BarDataSet(entries, getString(R.string.stats_pest_composition))
        dataSet.colors = colors
        dataSet.valueTextColor = textColor
        dataSet.valueTextSize = 10f

        val barData = BarData(dataSet)
        barData.barWidth = 0.6f

        binding.horizontalBarChart.apply {
            this.data = barData
            description.isEnabled = false
            legend.isEnabled = false
            
            setExtraOffsets(40f, 0f, 0f, 0f) 
            setDrawValueAboveBar(true)
            setFitBars(true)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
                this.textColor = textColor
                setLabelCount(labels.size)
            }
            
            axisLeft.apply {
                setDrawGridLines(false)
                granularity = 1f
                this.textColor = textColor
            }
            axisRight.isEnabled = false
            
            setNoDataTextColor(textColor)
            animateY(1000)
            invalidate()
        }
    }

    private fun updateTrendChart(timeframe: String) {
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        val calendar = Calendar.getInstance()

        val isDay = timeframe == getString(R.string.stats_time_day)
        val isWeek = timeframe == getString(R.string.stats_time_week)
        val isMonth = timeframe == getString(R.string.stats_time_month)

        if (isDay) {
            for (i in 6 downTo 0) {
                calendar.time = Date()
                calendar.add(Calendar.DAY_OF_YEAR, -i)
                val dateStr = SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.time)
                val start = getStartOfDay(calendar.timeInMillis)
                val end = start + (24 * 60 * 60 * 1000)
                val count = calculatePestSum(start, end)
                entries.add(Entry((6 - i).toFloat(), count.toFloat()))
                labels.add(dateStr)
            }
        } else if (isWeek) {
            for (i in 3 downTo 0) {
                calendar.time = Date()
                calendar.add(Calendar.WEEK_OF_YEAR, -i)
                val weekLabel = "Wk ${calendar.get(Calendar.WEEK_OF_YEAR)}"
                val start = getStartOfWeek(calendar.timeInMillis)
                val end = start + (7L * 24 * 60 * 60 * 1000)
                val count = calculatePestSum(start, end)
                entries.add(Entry((3 - i).toFloat(), count.toFloat()))
                labels.add(weekLabel)
            }
        } else if (isMonth) {
            for (i in 5 downTo 0) {
                calendar.time = Date()
                calendar.add(Calendar.MONTH, -i)
                val monthLabel = SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time)
                val start = getStartOfMonth(calendar.timeInMillis)
                val nextMonthCal = Calendar.getInstance().apply { 
                    timeInMillis = start
                    add(Calendar.MONTH, 1)
                }
                val end = nextMonthCal.timeInMillis
                val count = calculatePestSum(start, end)
                entries.add(Entry((5 - i).toFloat(), count.toFloat()))
                labels.add(monthLabel)
            }
        }
        setupLineChart(entries, labels)
    }

    private fun calculatePestSum(start: Long, end: Long): Int {
        return allHistoryItems.filter { it.timestamp in start until end }.sumOf {
            if (it is HistoryItem.IdentificationItem) {
                if (isBeneficial(it.insectName.lowercase())) 0 else 1
            } else {
                val countItem = it as HistoryItem.CountItem
                var pestSum = 0
                countItem.getBreakdownMap().forEach { (name, count) ->
                    if (!isBeneficial(name.lowercase())) pestSum += count
                }
                pestSum
            }
        }
    }

    private fun getStartOfDay(ts: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun getStartOfWeek(ts: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        return getStartOfDay(cal.timeInMillis)
    }

    private fun getStartOfMonth(ts: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return getStartOfDay(cal.timeInMillis)
    }

    private fun setupLineChart(data: List<Entry>, labels: List<String>) {
        val textColor = getThemeColor(android.R.attr.textColorPrimary)
        val errorRed = ContextCompat.getColor(requireContext(), R.color.error_red)

        val dataSet = LineDataSet(data, getString(R.string.stats_detections_over_time))
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.color = errorRed
        dataSet.setCircleColor(errorRed)
        dataSet.lineWidth = 3f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = errorRed
        dataSet.fillAlpha = 50

        dataSet.valueTextSize = 10f
        dataSet.valueTextColor = textColor
        dataSet.setDrawValues(true)

        val lineData = LineData(dataSet)
        
        binding.lineChart.apply {
            this.data = lineData
            description.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
                this.textColor = textColor
            }
            axisLeft.apply {
                granularity = 1f
                this.textColor = textColor
            }
            axisRight.isEnabled = false
            setNoDataTextColor(textColor)
            animateX(800)
            invalidate()
        }
    }

    @ColorInt
    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
