package com.example.mymemory.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mymemory.databinding.ActivityCreateBinding
import com.example.mymemory.models.BoardSize
import com.example.mymemory.utils.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream


class CreateActivity : AppCompatActivity() {


    companion object{
        const val PICK_PHOTO_CODE = 1313
        const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        const val READ_EXTERNAL_PHOTOS_CODE= 248
        const val TAG = "CreateActivity"
        const val MIN_GAME_NAME_LENGTH = 3
        const val MAX_GAME_NAME_LENGTH = 14
    }
    lateinit var binding: ActivityCreateBinding

    private lateinit var adapter:ImagePickerAdapter
    private lateinit var boardSize: BoardSize
    private var numImagesRequired = -1
    private val chosenImageUris = mutableListOf<Uri>()
    private val db = Firebase.firestore
    private val storage = Firebase.storage



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        getData()
        initRecycler()
        initListeners()

        binding.GameNameEv.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        binding.GameNameEv.addTextChangedListener(object : TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
                binding.btnSave.isEnabled = shouldEnableSaveButton()
            }

        })
    }

    private fun initListeners() {
        binding.btnSave.setOnClickListener {
            saveDataToFirebase()
        }
    }

    private fun saveDataToFirebase() {
        Log.i(TAG,"saveDataToFirebase")
        binding.btnSave.isEnabled = false
        val customGameName = binding.GameNameEv.text.toString()
        db.collection("games").document(customGameName).get()
            .addOnSuccessListener { document->
                if(document!=null&& document.data!=null){
                    AlertDialog.Builder(this)
                        .setTitle("Name taken")
                        .setMessage("A game already exists with the name $customGameName. Please choose another")
                        .setPositiveButton("OK",null)
                        .show()
                    binding.btnSave.isEnabled = true
                }else{
                    handleImageUploading(customGameName)
                }
            }.addOnFailureListener {exception->
                Log.e(TAG,"error while saving the game",exception)
                Toast.makeText(this, "error while saving the game", Toast.LENGTH_SHORT).show()
                binding.btnSave.isEnabled = true

            }
    }

    private fun handleImageUploading(customGameName: String) {
        binding.progressBar.visibility = View.VISIBLE
        var didEncouterError = false
        val uploadedImageUrls = mutableListOf<String>()
        for ((index, photoUri) in chosenImageUris.withIndex()){
            val imageByteArray  =  getImageByteArray(photoUri)
            val filePath = "images/$customGameName/${System.currentTimeMillis()}-${index}.jpg"
            var photoReference =storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray)
                .continueWithTask {photoUploadTask->
                    Log.i(TAG,"Uploaded bites: ${photoUploadTask.result?.bytesTransferred}")
                    photoReference.downloadUrl
                }.addOnCompleteListener {downloadUrlTask->
                    if(!downloadUrlTask.isSuccessful){
                        Log.e(TAG,"Exception with Firebase storage",downloadUrlTask.exception)
                        Toast.makeText(this, "Failed to upload images", Toast.LENGTH_SHORT).show()
                        didEncouterError = true
                        return@addOnCompleteListener
                    }
                    if(didEncouterError){
                        binding.progressBar.visibility = View.GONE
                        return@addOnCompleteListener
                    }
                    val downloadUrl = downloadUrlTask.result.toString()
                    uploadedImageUrls.add(downloadUrl)
                    binding.progressBar.progress = uploadedImageUrls.size / chosenImageUris.size
                    Log.i(TAG,"Finished uploading $photoUri,num: ${uploadedImageUrls.size}")
                    if(uploadedImageUrls.size == chosenImageUris.size){
                        handleAllImagesUploaded(customGameName,uploadedImageUrls)
                    }
                }

        }
    }

    private fun handleAllImagesUploaded(customGameName: String, imageUrls: MutableList<String>) {

        db.collection("games").document(customGameName)
            .set(mapOf("images" to imageUrls))
            .addOnCompleteListener {gameCreationTask->
                if(!gameCreationTask.isSuccessful){
                    Log.e(TAG,"Exeption with game creation",gameCreationTask.exception)
                    Toast.makeText(this, "Failed game creation", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                binding.progressBar.visibility = View.GONE
                Log.i(TAG,"Successfully reted game$customGameName")
                AlertDialog.Builder(this)
                    .setTitle("Upload complete! \nLets play your game $customGameName")
                    .setPositiveButton("OK"){_,_->
                        val resultData = Intent()
                        resultData.putExtra(GAME_NAME_EXTRA,customGameName)
                        setResult(Activity.RESULT_OK,resultData)
                        finish()
                    }.show()


            }

    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitmap = if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.P){
            val source = ImageDecoder.createSource(contentResolver,photoUri)
            ImageDecoder.decodeBitmap(source)
        }else{
            MediaStore.Images.Media.getBitmap(contentResolver,photoUri)
        }
        Log.i(TAG,"Original width ${originalBitmap.width} and height ${originalBitmap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap,250)
        Log.i(TAG,"Scaled width ${scaledBitmap.width} and height ${scaledBitmap.height}")
       val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG,60,byteOutputStream)
        return byteOutputStream.toByteArray()

    }

    private fun initRecycler() {
       adapter = ImagePickerAdapter(this,chosenImageUris,boardSize,
        object : ImagePickerAdapter.ImageClickListener{
            override fun onPlaceHolderClicked() {
                if(isPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION)){
                    launchIntentForPhotos()
                }else{
                    requestPermission(this@CreateActivity, READ_PHOTOS_PERMISSION,
                        READ_EXTERNAL_PHOTOS_CODE)
                }
              }
        })
        binding.rvImagePicker.adapter = adapter
        binding.rvImagePicker.setHasFixedSize(true)
        binding.rvImagePicker.layoutManager = GridLayoutManager(this,boardSize.getWidth())
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode== READ_EXTERNAL_PHOTOS_CODE){
            if(grantResults.isNotEmpty()&& grantResults[0]==PackageManager.PERMISSION_GRANTED){
                launchIntentForPhotos()
            }else{
                Toast.makeText(this, "In order to create custom game you need to provide access to your photos", Toast.LENGTH_SHORT).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    private fun getData() {
      boardSize =  intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = "Choose pics(0/$numImagesRequired)"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
       if(item.itemId==android.R.id.home ){
           finish()
           return true
       }
        return super.onOptionsItemSelected(item)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode != PICK_PHOTO_CODE || resultCode!= Activity.RESULT_OK || data ==null){
            Log.w(TAG, "Did not get the data back from the launched activity, user likely canceled flow")
            return
        }
       val selectedUri =  data.data
        val clipData = data.clipData
        if(clipData!= null){
            Log.i(TAG,"clipData numImages ${clipData.itemCount}")
            for (i in 0 until clipData.itemCount){
                val clipItem = clipData.getItemAt(i)
                if(chosenImageUris.size<numImagesRequired){
                    chosenImageUris.add(clipItem.uri)
                }
            }
        }else if (selectedUri!= null){
            Log.i(TAG,"data: $selectedUri")
            chosenImageUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose pics ${chosenImageUris.size} / $numImagesRequired)"
        binding.btnSave.isEnabled = shouldEnableSaveButton()
    }

    private fun shouldEnableSaveButton(): Boolean {
        if(chosenImageUris.size!= numImagesRequired){
            return false
        }
        if(binding.GameNameEv.text.isBlank()|| binding.GameNameEv.text.length<3){
            return false

        }
        return true
    }

    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/* "
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
        startActivityForResult(Intent.createChooser(intent,"Choose Pics "),PICK_PHOTO_CODE)
    }
}