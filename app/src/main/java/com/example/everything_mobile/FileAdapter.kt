package com.example.everything_mobile
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 데이터 클래스
data class FileData(
    val name: String,
    val size: Long,
    val date: Long,
    val path: String,
    val isFolder: Boolean
)

class FileAdapter(private var fileList: List<FileData>) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    var onItemClick: ((FileData) -> Unit)? = null
    var onItemLongClick: ((FileData, ContextMenu) -> Unit)? = null

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvFileDetail: TextView = view.findViewById(R.id.tvFileDetail)
        val tvFilePath: TextView = view.findViewById(R.id.tvFilePath) // ★ 이 줄이 있어야 세 번째 줄을 조종할 수 있습니다!
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)

        init {
            itemView.setOnCreateContextMenuListener { menu, v, menuInfo ->
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    val item = fileList[bindingAdapterPosition]
                    onItemLongClick?.invoke(item, menu)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_row, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = fileList[position]
        holder.tvFileName.text = item.name

        // 날짜 포맷
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.date * 1000))
        
        // 크기 포맷
        val sizeText = if (item.isFolder) {
            "폴더"
        } else {
            when {
                item.size >= 1024 * 1024 -> "%.1f MB".format(item.size / (1024.0 * 1024.0))
                item.size >= 1024 -> "%.1f KB".format(item.size / 1024.0)
                else -> "${item.size} B"
            }
        }

        // ★ [수정됨 1] 두 번째 줄에는 "크기 • 날짜"만 표시 (경로 뺌)
        holder.tvFileDetail.text = "$sizeText • $dateFormat"
        
        // ★ [수정됨 2] 세 번째 줄에 "경로"를 따로 표시
        holder.tvFilePath.text = item.path

        // 아이콘 설정
        if (item.isFolder) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder)
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_file)
        }

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount() = fileList.size

    fun updateList(newList: List<FileData>) {
        fileList = newList
        notifyDataSetChanged()
    }
}
