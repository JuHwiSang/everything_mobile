package com.example.everything_mobile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.everything_mobile.R

data class FileData(val name: String, val details: String, val isFolder: Boolean)

class FileAdapter(private val fileList: List<FileData>) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvFileDetail: TextView = view.findViewById(R.id.tvFileDetail)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_row, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = fileList[position]
        holder.tvFileName.text = item.name
        holder.tvFileDetail.text = item.details

        if (item.isFolder) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder) // 노란 폴더
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_file)   // 회색 파일
        }
    }

    override fun getItemCount() = fileList.size
}