package com.example.mymemory.ui

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.mymemory.R
import com.example.mymemory.models.BoardSize
import kotlin.math.min

class ImagePickerAdapter(
   private val context: Context,
   private val chosenImageUris: List<Uri>,
    private val boardSize: BoardSize,
   private val imageClickListener: ImageClickListener
) : RecyclerView.Adapter<ImagePickerAdapter.ViewHolder>() {

    interface ImageClickListener{
       fun onPlaceHolderClicked()
    }


    inner class ViewHolder (itemView: View): RecyclerView.ViewHolder(itemView){

        private val ivCustomImage = itemView.findViewById<ImageView>(R.id.customImage)


        fun bind(uri: Uri) {
           ivCustomImage.setImageURI(uri)
            ivCustomImage.setOnClickListener(null)
        }

        fun bind() {
            ivCustomImage.setOnClickListener {
              imageClickListener.onPlaceHolderClicked()
            }
        }

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val view = LayoutInflater.from(context).inflate(R.layout.card_image,parent,false)
       val  cardWidth=  parent.width/boardSize.getWidth()
       val  cardHeight=  parent.height/boardSize.getHeight()
        val cardSideLength = min(cardWidth,cardHeight)
       val layoutParams  =  view.findViewById<ImageView>(R.id.customImage).layoutParams
        layoutParams.width = cardSideLength
        layoutParams.height = cardSideLength

        return ViewHolder(view)


    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(position<chosenImageUris.size){
            holder.bind(chosenImageUris[position])
        }else{
            holder.bind()
        }

    }

    override fun getItemCount(): Int  =boardSize.getNumPairs()

}