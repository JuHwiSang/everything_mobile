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

// ★ 수정됨: 정렬을 위해 크기와 날짜를 숫자로 받음
data class FileData(
    val name: String,
    val size: Long,      // 바이트 단위 크기 (폴더면 0)
    val date: Long,      // 타임스탬프 날짜
    val path: String,
    val isFolder: Boolean
)

class FileAdapter(private var fileList: List<FileData>) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    var onItemClick: ((FileData) -> Unit)? = null
    var onItemLongClick: ((FileData, ContextMenu) -> Unit)? = null

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvFileDetail: TextView = view.findViewById(R.id.tvFileDetail)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)

        init {
            // 롱 클릭 리스너 설정
            itemView.setOnCreateContextMenuListener { menu, v, menuInfo ->
                // 클릭된 뷰와 위치(position)를 밖으로 전달
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

        // ★ 추가됨: 날짜와 크기를 보기 좋게 변환하는 로직
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.date * 1000)) // sec -> milisec
        
        val sizeText = if (item.isFolder) {
            "폴더"
        } else {
            // 바이트를 MB, KB로 변환
            when {
                item.size >= 1024 * 1024 -> String.format("%.1f MB", item.size / (1024.0 * 1024.0))
                item.size >= 1024 -> String.format("%.1f KB", item.size / 1024.0)
                else -> "${item.size} B"
            }
        }

        // 화면에 "크기, 날짜" 형태로 표시
        holder.tvFileDetail.text = "$sizeText • $dateFormat"

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

    // ★ 추가됨: 정렬된 리스트로 갈아끼우는 함수
    fun updateList(newList: List<FileData>) {
        fileList = newList
        notifyDataSetChanged()
    }
}
