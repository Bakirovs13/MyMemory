package com.example.mymemory.ui

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mymemory.R
import com.example.mymemory.databinding.ActivityMainBinding
import com.example.mymemory.models.BoardSize
import com.example.mymemory.models.MemoryGame
import com.example.mymemory.models.UserImageList
import com.example.mymemory.ui.CreateActivity.Companion.TAG
import com.example.mymemory.utils.EXTRA_BOARD_SIZE
import com.example.mymemory.utils.GAME_NAME_EXTRA
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    companion object{
        private const val CREATE_REQUEST_CODE = 666
    }

    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter
    lateinit var binding:ActivityMainBinding
    private var boardSize:BoardSize = BoardSize.EASY

    private val db = Firebase.firestore
    private var gameName :String? = null
    private var customGameImages:List<String>?= null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initRecycler()


    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
      menuInflater.inflate(R.menu.menu_main,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog("Quit your current game?", null, View.OnClickListener {
                        initRecycler()
                    })
                } else {
                    initRecycler()
                    return true
                }
            }
            R.id.new_size ->{
                showNewSizeDialog()
                return true
            }
            R.id.create_custom_game ->{
                showCreationDialog()
                return true
            }
            R.id.download_game->{
                showDownloadDialog()
            }

        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDownloadDialog() {
       val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Fetch memory game", boardDownloadView,View.OnClickListener {
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    private fun showCreationDialog() {
        val boardSizeView =   LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        showAlertDialog("Create your own memory board", boardSizeView,View.OnClickListener {
           val desiredBoardSIze =  when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy ->BoardSize.EASY
                R.id.rbMedium ->BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            //navigate to the new activity
            val intent = Intent(this,CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE,desiredBoardSIze)
            startActivityForResult(intent,CREATE_REQUEST_CODE)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == CREATE_REQUEST_CODE&& resultCode == Activity.RESULT_OK){
            val customGameName= data?.getStringExtra(GAME_NAME_EXTRA)
            if(customGameName==null){
                Log.e(TAG,"Got null custom game from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)

    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document->
           val userImageList =  document.toObject(UserImageList::class.java)
            if(userImageList?.images == null ){
                Log.e(TAG,"Invalid custom game data from firestore")
                Snackbar.make(binding.clRoot,"Sorry,we could not find any such game $customGameName",Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size*2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for(imageUrl in userImageList.images){
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(binding.clRoot,"You're now playing '$customGameName'",Snackbar.LENGTH_LONG).show()
            gameName = customGameName
            initRecycler() 
        }.addOnFailureListener {exception->
            Log.e(TAG,"Exception when retrieving game ",exception)


        }
    }

    private fun showNewSizeDialog() {
     val boardSizeView =   LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
       val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
       when(boardSize){
           BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
           BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
           BoardSize.HARD ->  radioGroupSize.check(R.id.rbHard)
       }
        showAlertDialog("Choose new size", boardSizeView,View.OnClickListener {
            boardSize = when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy ->BoardSize.EASY
                R.id.rbMedium ->BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            initRecycler()

       })
    }

    private fun showAlertDialog(title:String,view: View?,positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Ok"){_,_->
                positiveClickListener.onClick(null)
            }.show()

    }

    private fun initRecycler() {
        supportActionBar?.title = gameName?: getString(R.string.app_name)
        when(boardSize){

            BoardSize.EASY ->{
                binding.tvNumMoves.text = "Easy : 4*2"
                binding.tvNumPairs.text = "Pairs : 0/4"
            }
            BoardSize.MEDIUM ->{
                binding.tvNumMoves.text = "Easy : 6*3"
                binding.tvNumPairs.text = "Pairs : 0/9"
            }
            BoardSize.HARD ->{
                binding.tvNumMoves.text = "Easy : 6*6"
                binding.tvNumPairs.text = "Pairs : 0/12"
            }

        }
        binding.tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize,customGameImages)
        binding.apply {
            adapter=  MemoryBoardAdapter(this@MainActivity,
                boardSize,
                memoryGame.cards,
                object : MemoryBoardAdapter.CardClickListener {
                    override fun onCardClick(position: Int) {
                        updateGameWithFLip(position)
                    } }
            )
            rvBoard.adapter = adapter
            rvBoard.setHasFixedSize(true)
            rvBoard.layoutManager = GridLayoutManager(this@MainActivity,boardSize.getWidth())
        }
    }


    private fun updateGameWithFLip(position: Int) {
        if(memoryGame.haveWonGame()){
            Snackbar.make(binding.clRoot,"You already won!",Snackbar.LENGTH_LONG).show()

            return
        }
        if(memoryGame.isCardFaceUp(position)){
            Snackbar.make(binding.clRoot,"Invalid move!",Snackbar.LENGTH_SHORT).show()

        }


        if(memoryGame.flipCard(position)){
            Toast.makeText(this, "found a match ${memoryGame.numPairsFound} ", Toast.LENGTH_SHORT).show()
            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat()/boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full),
            )as Int

            binding.tvNumPairs.setTextColor(color)  //<----
           binding.tvNumPairs.text = "Pairs:${memoryGame.numPairsFound} /${boardSize.getNumPairs()}"

            if(memoryGame.haveWonGame()){
                Snackbar.make(binding.clRoot,"You won!Congratulations.",Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(binding.clRoot,
                    intArrayOf(Color.YELLOW,Color.RED,Color.BLUE,Color.GREEN,Color.WHITE,Color.MAGENTA))
                    .oneShot()
            }
        }
        binding.tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }

}